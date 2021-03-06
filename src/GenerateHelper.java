import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.file.PsiDirectoryImpl;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression;
import com.jetbrains.php.lang.psi.elements.impl.PhpPsiElementImpl;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Created by Tomasz on 19/04/2017.
 */
public class GenerateHelper extends AnAction {

    private Project project;

    private PsiFile appConfig;

    private PsiElement appConfigProvidersElement;

    private String command = "";

    @Override
    public void actionPerformed(AnActionEvent e) {
        final Project project = e.getProject();
        if (project == null) {
            return;
        }

        this.project = project;

        if (!this.existsIdeHelper()) {
            this.addUpdateComposerCommand();
            this.initProvider();
        }

        this.generateHelpers();
    }

    private boolean generateHelpers() {
        this.addGenerateIdeHelperCommand();
        this.addGenerateModelsDocsCommand();
        this.executeCommand(this.command);
        return true;
    }

    private boolean found;

    private boolean existsIdeHelper() {
        PsiFile files[] = FilenameIndex.getFilesByName(this.project, "app.php", GlobalSearchScope.projectScope(this.project));


        for (PsiFile file : files) {
            if (file.getContainingDirectory().getName().equals("config")) {

                file.accept(new PsiRecursiveElementWalkingVisitor() {
                    @Override
                    public void visitElement(PsiElement element) {
                        if (found && element instanceof ArrayCreationExpression) {
                            found = phpArrayContainsValue(element.getChildren(), "Barryvdh\\LaravelIdeHelper\\IdeHelperServiceProvider::class", element);
                            setAppConfig(file, element);
                            if (found) {
                                this.stopWalking();
                                return;
                            }
                        }

                        if (element.getText().equals("'providers'"))
                            found = true;

                        super.visitElement(element);
                    }
                });

                return found;
            }
        }

        return false;
    }

    private void setAppConfig(PsiFile file, PsiElement element) {
        this.appConfigProvidersElement = element;
        this.appConfig = file;
    }

    private void initProvider() {
        Runnable r = () -> insertProvider();
        WriteCommandAction.runWriteCommandAction(project, r);
    }

    private void insertProvider() {
        Document document = PsiDocumentManager.getInstance(project).getDocument(this.appConfig);
        if (this.appConfigProvidersElement.getLastChild().getPrevSibling().getPrevSibling().getText().equals(",")) {
            document.insertString(this.appConfigProvidersElement.getLastChild().getPrevSibling().getTextOffset(), "\n        Barryvdh\\LaravelIdeHelper\\IdeHelperServiceProvider::class,");
        } else {
            document.insertString(this.appConfigProvidersElement.getLastChild().getPrevSibling().getTextOffset(), ",\n        Barryvdh\\LaravelIdeHelper\\IdeHelperServiceProvider::class,");
        }
    }

    private boolean phpArrayContainsValue(PsiElement[] elements, String value, PsiElement origin) {
        for (PsiElement element : elements) {
            if (element.getText().replaceAll("\'", "").equals(value)) {
                elements[elements.length - 1] = new PhpPsiElementImpl(origin.getNode());
                return true;
            }
        }
        return false;
    }


    private void addUpdateComposerCommand() {
        this.command = "composer require barryvdh/laravel-ide-helper --dev";
    }

    private void addGenerateIdeHelperCommand() {
        if (!this.command.equals("")) this.command = this.command + " &&";
        this.command += " php artisan ide-helper:generate";
    }

    private boolean addGenerateModelsDocsCommand() {
        PsiDirectory pluginsDirectory = this.getPluginsDirectory();

        if (pluginsDirectory == null) return false;

        PsiElement[] pluginsAuthors = pluginsDirectory.getChildren();

        if (pluginsAuthors.length > 0) {
            for (int i = 0; i < pluginsAuthors.length; i++) {
                PsiDirectoryImpl authorDir = (PsiDirectoryImpl) pluginsAuthors[i];
                PsiElement[] authorPlugins = authorDir.getChildren();
                if (authorPlugins.length > 0) {
                    for (int j = 0; j < authorPlugins.length; j++) {
                        PsiDirectoryImpl pluginDirectory = (PsiDirectoryImpl) authorPlugins[j];
                        PsiDirectory directory = pluginDirectory.findSubdirectory("models");
                        if (directory != null) {
                            this.command += " && php artisan ide-helper:models --dir=\"plugins/" + authorDir.getName() + "/" + pluginDirectory.getName() + "/models\"";
                        }
                    }
                }
            }
        }

        return false;
    }

    @Nullable
    private PsiDirectory getPluginsDirectory() {
        PsiFileSystemItem psiFileSystemItems[] = FilenameIndex.getFilesByName(project, "plugins", GlobalSearchScope.projectScope(project), true);

        PsiDirectory psiDirectory = null;

        for (int i = 0; i < psiFileSystemItems.length; i++) {
            psiDirectory = (PsiDirectory) psiFileSystemItems[i];
            if (psiDirectory.getParent() != null &&
                    psiDirectory.getParent().isDirectory() &&
                    psiDirectory.getParent().getName().equals("public") ||
                    psiDirectory.getParent().getName().equals(project.getBaseDir().getName())) {
                break;
            }
        }
        return psiDirectory;
    }

    private boolean executeCommand(String command) {

        try {
            Runtime rt = Runtime.getRuntime();
            ProcessBuilder pb = new ProcessBuilder("cmd", "/c", "start", "cmd.exe", "/K", "cd " + this.project.getBasePath() + "/public/ && " + command + " && exit");
            Process proc = pb.start(); // Start the process.

            InputStream stderr = proc.getErrorStream();
            InputStreamReader isr = new InputStreamReader(stderr);
            BufferedReader br = new BufferedReader(isr);
            String line = null;
            System.out.println("command = [" + command + "]");
            System.out.println("<ERROR>");
            while ((line = br.readLine()) != null)
                System.out.println(line);
            System.out.println("</ERROR>");
            int exitVal = proc.waitFor();
            System.out.println("Process exitValue: " + exitVal);


            System.out.println("Script executed successfully");
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
}
