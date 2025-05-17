package com.huq.idea.flow.apidoc;

import com.huq.idea.flow.model.CallStack;
import com.huq.idea.flow.model.MethodDescription;
import com.huq.idea.flow.util.MyPsiUtil;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.JavaRecursiveElementVisitor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiMethodReferenceExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.search.searches.DefinitionsScopedSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Query;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * <p>
 * 整体流程：
 * <li>
 * 1. 从当前操作的方法开始调用 generateMethodChains,
 * </li>
 * </p>
 *
 * @author huqiang
 * @since 2024/7/13 15:58
 */
public class MethodChainVisitor extends JavaRecursiveElementVisitor {

    private static final Logger log = Logger.getInstance(MethodChainVisitor.class.getName());
    private final ArrayList<String> imfCache = new ArrayList<>();

    // 子类方法对应的父类方法
    private final Map<PsiMethod, PsiMethod> subMethodInterfaceMap = new HashMap<>();

    private CallStack topStack;
    private CallStack currentStack;


    /**
     * 提取方法调用的通用属性
     */
    private MethodDescription createMethodDescriptionFromExpression(PsiMethodCallExpression expression, PsiMethod psiMethod) {
        MethodDescription methodDescription = createMethodDescription(psiMethod);
        
        // 提取参数
        String parameters = Arrays.stream(expression.getArgumentList().getExpressions())
                .map(PsiElement::getText)
                .collect(Collectors.joining(", "));
        methodDescription.put("parameters", parameters);

        // 提取调用者对象
        PsiExpression qualifier = expression.getMethodExpression().getQualifierExpression();
        if (qualifier != null) {
            methodDescription.put("caller", qualifier.getText());
        }
        
        methodDescription.put("expression.text", expression.getText());
        
        return methodDescription;
    }

    private void processInternalMethodCall(PsiMethodCallExpression expression, PsiMethod psiMethod) {
        PsiCodeBlock currentMethodBody = currentStack.getMethodDescription().getPsiMethod().getBody();
        if (currentMethodBody == null) return;
        
        int startOffset = currentMethodBody.getTextRange().getStartOffset();
        if (currentStack.getCurrentOffset() == 0) {
            currentStack.setCurrentOffset(startOffset);
        }
        
        int offset = currentStack.getCurrentOffset() - startOffset;
        PsiStatement parentStatement = PsiTreeUtil.getParentOfType(expression, PsiStatement.class);
        if (parentStatement == null) return; // 修改点5：提前返回
        
        TextRange parentTextRange = parentStatement.getTextRange();
        String subBody = currentMethodBody.getText().substring(offset, parentTextRange.getEndOffset() - startOffset);
        
        MethodDescription methodDescription = createMethodDescriptionFromExpression(expression, psiMethod);
        methodDescription.put("external", "false");
        methodDescription.put("parentStatement", parentStatement.getText());
        methodDescription.put("expression.subBody", subBody);
        
        currentStack.methodCall(methodDescription);
        currentStack.setCurrentOffset(parentTextRange.getEndOffset());
    }

    private void processExternalMethodCall(PsiMethodCallExpression expression, PsiMethod psiMethod) {
        MethodDescription methodDescription = createMethodDescriptionFromExpression(expression, psiMethod);
        methodDescription.put("external", "true");
        currentStack.methodCall(methodDescription);
    }

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
        super.visitMethodCallExpression(expression);

        PsiMethod callingMethod = PsiTreeUtil.getParentOfType(expression, PsiMethod.class);
        PsiMethod calledMethod = expression.resolveMethod();
        log.info("visitMethodCallExpression: " + expression.getText());

        if (callingMethod != null && calledMethod != null) {
            PsiClass containingClass = calledMethod.getContainingClass();
            if (containingClass == null) return;

            String className = containingClass.getName();
            // 过滤条件保持不变
            if ((className.endsWith("Impl") || className.contains("Adapter") 
                    || className.contains("Service") || className.contains("Api"))
                    && !(className.endsWith("Util") || className.endsWith("Utils"))) {
                
                if (MyPsiUtil.isInJavaFile(containingClass)) {
                    processInternalMethodCall(expression, calledMethod);
                } else if (className.toLowerCase().endsWith("api")) {
                    processExternalMethodCall(expression, calledMethod);
                }
            }
        }
    }

    private void methodAccept(PsiElement psiElement) {
        if (psiElement instanceof PsiMethod) {
            PsiMethod method = (PsiMethod) psiElement;

            PsiClass containingClass = (method).getContainingClass();
            if (containingClass == null) {
                return;
            }
            boolean inJavaFile = MyPsiUtil.isInJavaFile(containingClass);
            if (/*params.isSmartInterface() && */inJavaFile && !imfCache.contains(containingClass.getQualifiedName())) {
//                    containingClass.accept(new ImplementationFinder());
                imfCache.add(containingClass.getQualifiedName());
            }
            if (inJavaFile) {
                method.accept(this);
            }
        }
    }

    private boolean alreadyInStack(PsiMethod psiMethod) {
        // Don't check external method, because the getTextOffset() will cause Java decompiler, it will wast of time.
        if (psiMethod.getContainingClass() == null || MyPsiUtil.isExternal(psiMethod.getContainingClass())) return true;
        final int offset = psiMethod.getTextOffset();
        MethodDescription method = createMethodDescription(psiMethod);
        return currentStack.isRecursive(method);
    }

    private MethodDescription createMethodDescription(PsiMethod psiMethod) {
        PsiDocComment docComment = psiMethod.getDocComment();
        if (psiMethod.getDocComment() == null) {
            PsiMethod superMethod = subMethodInterfaceMap.get(psiMethod);
            if (superMethod != null) {
                docComment = superMethod.getDocComment();
            }
        }
        return new MethodDescription(psiMethod, psiMethod.getContainingClass().getQualifiedName(), psiMethod.getText(), psiMethod.getName(), docComment, psiMethod.getReturnType().getPresentableText());
    }


    @Override
    public void visitMethod(PsiMethod method) {
        if (method.getContainingClass() == null) {
            return;
        }
        log.info("visitMethod: " + method.getContainingClass().getQualifiedName() + " --> " + method.getName());

        // 跳过接口的方法调用链路， 因为最终会找到实现类的方法
        if (MyPsiUtil.isInterface(Objects.requireNonNull(method.getContainingClass().getModifierList()))) {
            return;
        }
        MethodDescription methodDescription = createMethodDescription(method);
        methodDescription.put("method.init","true");
        if (makeMethodCallExceptCurrentStackIsRecursive(methodDescription)) {
            return;
        }
        super.visitMethod(method);

    }

    @Override
    public void visitStatement(PsiStatement statement) {
        // 该方法每次可以获取到一个完整的代码块
        // 当碰到类似 if语句时，
        // 1. 会先获取到完整的 if代码块，包含 else中的代码
        // 2. 获取 if 中的代码块
        // 3. 获取 if 中每行的代码
        // 4. 获取 else中的完整代码块
        // 5. 获取 else中的每行代码
        super.visitStatement(statement);
        /*if (statement instanceof CompositePsiElement) {
            IElementType elementType = ((CompositeElement) statement).getElementType();
            switch (elementType.getDebugName()){
                case "IF_STATEMENT":
                    log.info("visitStatement-IF_STATEMENT: " + statement.getText());
                    PsiIfStatement psiIfStatement = (PsiIfStatement) statement;
                    if (psiIfStatement.getCondition() != null) {
                        log.info("condition: "+psiIfStatement.getCondition().getText());
                    }
                    if (psiIfStatement.getElseBranch() != null) {
                        log.info("elseBranch: "+psiIfStatement.getElseBranch().getText());
                    }
                    if (psiIfStatement.getThenBranch() != null) {
                        log.info("thenBranch: "+psiIfStatement.getThenBranch().getText());
                    }
                    break;
                default:
                    log.info("visitStatement-CompositeElement : " + elementType+", "+statement.getText());
                    break;
            }
        }*/
    }

    @Override
    public void visitMethodReferenceExpression(PsiMethodReferenceExpression expression) {
        log.info("visitMethodReferenceExpression: " + expression.getText());
    }


    @Override
    public void visitReferenceExpression(PsiReferenceExpression expression) {
        super.visitReferenceExpression(expression);
//        log.info("visitReferenceExpression: " + expression.getText());
    }


    public CallStack generateMethodChains(PsiElement element, CallStack parentStack) {
        if (parentStack != null) {
            topStack = parentStack;
            currentStack = parentStack;
        }
        if (element instanceof PsiMethod) {
            generate((PsiMethod) element);
        }
        return topStack;
    }

    private CallStack generate(PsiMethod psiMethod) {
        if (psiMethod.getLanguage().equals(JavaLanguage.INSTANCE)) {
            return generateJava(psiMethod);
        } else {
            return topStack;
        }
    }

    private CallStack generateJava(PsiMethod psiMethod) {
        PsiClass containingClass = psiMethod.getContainingClass();
        if (containingClass == null) {
            containingClass = (PsiClass) psiMethod.getParent().getContext();
        }
        if (containingClass == null) {
            return topStack;
        }

        if (MyPsiUtil.isAbstract(containingClass)) {
            psiMethod.accept(this);
            Query<PsiElement> search = DefinitionsScopedSearch.search(psiMethod).allowParallelProcessing();
            for (PsiElement psiElement : search) {
                if (psiElement instanceof PsiMethod) {
                    if (alreadyInStack((PsiMethod) psiElement)) {
                        continue;
                    }
                    subMethodInterfaceMap.put((PsiMethod) psiElement, psiMethod);
                    methodAccept(psiElement);
                }
            }
        } else {
            methodAccept(psiMethod);
        }
        return topStack;
    }


    private boolean makeMethodCallExceptCurrentStackIsRecursive(MethodDescription method) {
        if (topStack == null) {
            topStack = new CallStack(method);
            currentStack = topStack;
        } else {
            if (currentStack.isRecursive(method)) {
                return true;
            }
            currentStack = currentStack.methodCall(method);
        }
        return false;
    }

}
