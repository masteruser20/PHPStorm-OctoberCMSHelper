import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.psi.*;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.SmartList;
import com.jetbrains.php.lang.psi.elements.ClassConstantReference;
import com.jetbrains.php.lang.psi.elements.PhpNamespace;
import com.jetbrains.php.lang.psi.elements.impl.ArrayCreationExpressionImpl;
import com.jetbrains.php.lang.psi.elements.impl.ArrayHashElementImpl;
import com.jetbrains.php.lang.psi.elements.impl.ArrayIndexImpl;
import com.jetbrains.php.lang.psi.elements.impl.ClassReferenceImpl;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Tomasz on 05/01/2017.
 */
public class GoToDeclaration extends AnAction {
    private static final NotificationGroup NOTIFICATION_GROUP = NotificationGroup.toolWindowGroup("Find in Path", ToolWindowId.FIND, false);

    private String name;

    private String namespace;

    private Collection<ClassConstantReference> classConstants;

    @Contract(pure = true)
    public static NotificationGroup getNotificationGroup() {
        return NOTIFICATION_GROUP;
    }

    final int COMPONENT = 1;

    final int PARTIAL = 2;

    private int callingType;

    @Contract(pure = true)
    private String getName() {
        return name;
    }

    private void setName(String name) {
        this.name = name;
    }

    @Contract(pure = true)
    private String getNamespace() {
        return namespace;
    }

    private void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    @Contract(pure = true)
    private Collection<ClassConstantReference> getClassConstants() {
        return classConstants;
    }

    private void setClassConstants(Collection<ClassConstantReference> classConstants) {
        this.classConstants = classConstants;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        clear();
        final Project project = e.getProject();
        if (project == null) {
            return;
        }


        this.callingType = COMPONENT;
        PsiManager.getInstance(project).getClass();
        Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();

        final Document document = editor != null ? editor.getDocument() : null;
        String selectedText = editor.getSelectionModel().getSelectedText();
        PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
        this.setClassConstants(getVariables(project));


        this.callingType = this.getCallingType(psiFile, editor);

        if (selectedText != null) {
            this.setName(selectedText);
        } else {
            PsiElement psiElement = psiFile.findElementAt(editor.getCaretModel().getOffset());
            String line = psiElement != null ? psiElement.getText() : null;

            if (line != "") {
                if (this.callingType == COMPONENT) {
                    Pattern p = Pattern.compile("\\[(.*?)\\]");
                    Matcher m = p.matcher(line);

                    while (m.find()) {
                        this.setName((m.group(1)));
                    }


                    if (this.getName() == null) {
                        this.setName(this.getNameFromComponentAlias(psiElement));
                    }
                }
                if (this.getName() == null) {
                    this.setName(line);
                }

            } else return;

        }

        VirtualFile file = null;
        switch (this.callingType) {
            case COMPONENT:
                Map<String, String> components = this.getComponentNames(project);
                file = checkIfComponentExists(components, project);
                break;
            case PARTIAL:
                String[] directories = this.checkIfNameContainsDirectory();
                file = this.getPartialFile(project, directories);
                break;
        }

        if (file != null) {
            FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
            fileEditorManager.openFile(file, true, true);
        }

    }

    @Nullable
    private String[] checkIfNameContainsDirectory() {
        if (this.name.contains("/")) {
            String[] splittedStrings = this.name.split("/");
            String lastElement = splittedStrings[splittedStrings.length - 1];
            splittedStrings = Arrays.copyOf(splittedStrings, splittedStrings.length - 1);
            this.name = lastElement;
            return splittedStrings;
        }

        return null;
    }

	/*
		Searches in set of directories or if directory not exists takes first matched file
	*/
    @Nullable
    private VirtualFile getPartialFile(Project project, String[] directories) {
        PsiFile[] files = FilenameIndex.getFilesByName(project, this.name + ".htm", GlobalSearchScope.allScope(project));

        if (directories != null && directories.length > 0) {
            for (int i = 0; i < files.length; i++) {
                if (files[i].getContainingDirectory().getName().equalsIgnoreCase(directories[directories.length - 1])) {
                    return files[i].getVirtualFile();
                }
            }
        } else {
            return files[0].getVirtualFile();
        }

        return null;
    }

	/*
		Process to refer to a specific calling type
		partial => opens partial.htm
		component => opens component.php module
	*/
    private int getCallingType(PsiElement psiFile, Editor editor) {
        PsiElement elem = psiFile.findElementAt(editor.getCaretModel().getOffset()).getPrevSibling();
        while (elem != null && !elem.getText().equals("{%")) {
            if (elem.getText().equals("partial")) {
                return PARTIAL;
            } else if (elem.getText().equals("component")) {
                return COMPONENT;
            }
            elem = elem.getPrevSibling();
        }

        return this.callingType;
    }

	/*
		Fetchs namespace of given component alias
	*/
    @Nullable
    private String getNameFromComponentAlias(PsiElement psiElement) {
        String line = psiElement.getText();
        if (line.charAt(0) == '\'' && line.charAt(line.length() - 1) == '\'') {
            return line.replaceAll("\'", "");
        }
        if (line.charAt(0) != '[' && line.charAt(line.length() - 1) == ']') {
            if (psiElement.getPrevSibling() == null || psiElement.getPrevSibling().getPrevSibling() == null)
                return null;
            psiElement = psiElement.getPrevSibling().getPrevSibling();
            line = psiElement.getText();
        }

        return (line != null) ? line.replaceAll("[\\[\\](){}]", "") : null;
    }

	/*
		Checks if component exists in set of plugins
	*/
    @Nullable
    private VirtualFile checkIfComponentExists(Map<String, String> components, Project project) {
        if (components.isEmpty()) return null;

        for (Map.Entry<String, String> entry : components.entrySet()) {
            if (entry.getValue().equals(this.getName())) {
                if (entry.getKey().contains("'") && entry.getKey().contains("\\")) {
                    this.setNamespace(entry.getKey().replaceAll("\'", ""));
                } else {
                    this.setNamespace(checkIfNamespaceIsVariable(entry.getKey()));
                }
            }

        }

        if (this.getNamespace() != null) {
            PsiFile componentFile = findFileByClassNamespace(project, this.getNamespace());
            if (componentFile != null) {
                return componentFile.getVirtualFile();
            }
        }
        return null;
    }

    private void clear() {
        this.setName(null);
        this.setNamespace(null);
    }

	/*
		Namespace found can be inside variable
	*/
    @Nullable
    private String checkIfNamespaceIsVariable(String variableName) {
        if (this.getClassConstants() == null || this.getClassConstants().isEmpty()) return null;

        for (ClassConstantReference constantReference : this.getClassConstants()) {
            if (constantReference.resolve() != null && constantReference.resolve().getLastChild().getText() != null) {
                if (constantReference.getText().equals(variableName)) {
                    return constantReference.resolve().getLastChild().getText();
                } else if (variableName.indexOf('.') > 0) {
                    String varReference = variableName.substring(0, variableName.indexOf('.')).trim();
                    String className = variableName.substring(variableName.indexOf('.') + 1).trim();
                    if (constantReference.getText().equals(varReference)) {
                        return "'" + constantReference.resolve().getLastChild().getText().replace("'", "") + className.replace("'", "") + "'";
                    }
                }
            } else if (constantReference.resolve() == null) {
                if (constantReference.getText().equals(variableName)) {
                    return (new ClassReferenceImpl(constantReference.getFirstChild().getNode())).getFQN();
                }
            }
        }

        return null;
    }

	
    @Nullable
    private PsiFile findFileByClassNamespace(Project project, String namespace) {
        final Collection<PsiFile> result = new SmartList<PsiFile>();
        String[] splitBckSlash = namespace.split("\\\\");
        String fileName = splitBckSlash[splitBckSlash.length - 1].replaceAll("\'", "");

        PsiFile[] files = FilenameIndex.getFilesByName(project, fileName + ".php", GlobalSearchScope.allScope(project));

        for (PsiFile file : files) {
            file.accept(new SelfRecursiveElementVisitor() {
                @Override
                public void visitPhpNamespace(PhpNamespace visitNamespace) {
                    if (visitNamespace.getElementType().toString().contains("Namespace") && compareNamespace(visitNamespace, namespace, fileName)) {
                        result.add(file);
                    }
                }
            });
        }

        if (!result.isEmpty()) {
            return result.iterator().next();
        }

        return null;
    }

    private boolean compareNamespace(PhpNamespace nam1, String nam2, String fileName) {
        nam2 = nam2.replaceAll("\\\\", "");
        nam2 = nam2.replaceAll("\'", "");
        String visitedNamespaceEscaped = nam1.getFQN().replaceAll("\\\\", "");
        visitedNamespaceEscaped += fileName;
        return visitedNamespaceEscaped.equalsIgnoreCase(nam2);
    }

    private Map<String, String> getComponentNames(Project project) {
        PsiFile[] files = FilenameIndex.getFilesByName(project, "Plugin.php", GlobalSearchScope.allScope(project));
        Map<String, String> components = new HashMap<String, String>();

        for (PsiFile file : files) {

            file.accept(new PsiRecursiveElementWalkingVisitor() {
                @Override
                public void visitElement(PsiElement element) {
                    if (element.getClass().getName().equals(ArrayCreationExpressionImpl.class.getName())) {
                        parsePhpArray(element, components);
                    }
                    super.visitElement(element);
                }

            });

        }
        return components;
    }


    private void parsePhpArray(PsiElement element, Map<String, String> components) {
        if (element.getFirstChild() != null) {
            parsePhpArray(element.getFirstChild(), components);
        }

        if (element.getNextSibling() != null) {
            parsePhpArray(element.getNextSibling(), components);
        }

        if (element.getClass().getName().equals(ArrayHashElementImpl.class.getName())) {
            PsiElement[] arrayKeyValue = new ArrayIndexImpl(element.getNode()).getChildren();
            if (arrayKeyValue.length == 2) {
                components.put(arrayKeyValue[0].getText(), arrayKeyValue[1].getText().replaceAll("\'", ""));
            }
        }
    }

	/*
		Indexes all available arrays of component variables
	*/
    private static Collection<ClassConstantReference> getVariables(Project project) {
        PsiFile[] files = FilenameIndex.getFilesByName(project, "Plugin.php", GlobalSearchScope.allScope(project));
        final Collection<ClassConstantReference> result = new SmartList<ClassConstantReference>();
        for (PsiFile file : files) {
            file.accept(new SelfRecursiveElementVisitor() {
                @Override
                public void visitPhpClassConstantReference(ClassConstantReference constantReference) {
                    result.add(constantReference);
                }
            });
        }
        return result;
    }


    @Override
    public void update(final AnActionEvent e) {
        //Get required data keys
        final Project project = e.getData(CommonDataKeys.PROJECT);
        final Editor editor = e.getData(CommonDataKeys.EDITOR);
        //Set visibility only in case of existing project and editor and if some text in the editor is selected
        e.getPresentation().setVisible((project != null && editor != null
                && editor.getSelectionModel().hasSelection()));
    }

    static void showNotAvailableMessage(AnActionEvent e, Project project) {
        final String message = "'" + e.getPresentation().getText() + "' is not available while search is in progress";
        getNotificationGroup().createNotification(message, NotificationType.WARNING).notify(project);
    }

}
