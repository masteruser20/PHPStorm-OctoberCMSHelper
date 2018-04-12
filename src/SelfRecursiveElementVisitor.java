import com.intellij.psi.PsiElementVisitor;

/**
 * Created by extreme on 13/01/2017.
 */
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.php.lang.psi.visitors.PhpElementVisitor;
import java.util.Iterator;
import java.util.List;

public abstract class SelfRecursiveElementVisitor extends PhpElementVisitor {
    private final boolean myVisitAllFileRoots;

    public SelfRecursiveElementVisitor() {
        this.myVisitAllFileRoots = false;
    }


    public void visitElement(PsiElement element) {
        for(PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            child.accept(this);
        }

    }

}