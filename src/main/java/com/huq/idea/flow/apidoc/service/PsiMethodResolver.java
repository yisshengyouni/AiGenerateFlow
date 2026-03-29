package com.huq.idea.flow.apidoc.service;

import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.DefinitionsScopedSearch;
import com.intellij.openapi.application.ReadAction;
import com.intellij.util.Query;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;

import java.util.ArrayList;
import java.util.List;

public class PsiMethodResolver {

    /**
     * Attempts to find a method's source code by its fully qualified signature.
     * Handles interface resolution and basic parameter matching for overloads.
     */
    public static String resolveClassSource(Project project, String fullyQualifiedClassName) {
        return ReadAction.compute(() -> {
            try {
                JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
                PsiClass psiClass = facade.findClass(fullyQualifiedClassName, GlobalSearchScope.allScope(project));

                if (psiClass == null) return null;

                StringBuilder sb = new StringBuilder();
                if (psiClass.getDocComment() != null) {
                    sb.append(psiClass.getDocComment().getText()).append("\n");
                }

                sb.append(psiClass.hasModifierProperty("public") ? "public " : "")
                  .append(psiClass.isInterface() ? "interface " : "class ")
                  .append(psiClass.getName()).append(" {\n\n");

                // Append Fields
                PsiField[] fields = psiClass.getFields();
                if (fields.length > 0) {
                    sb.append("  // === 字段定义 ===\n");
                    for (PsiField field : fields) {
                        if (field.getDocComment() != null) {
                            String comment = field.getDocComment().getText().replaceAll("\n", " ");
                            sb.append("  ").append(comment).append("\n");
                        }
                        sb.append("  ").append(field.getType().getPresentableText())
                          .append(" ").append(field.getName()).append(";\n\n");
                    }
                }

                // Append Method Signatures (no bodies) to save tokens
                PsiMethod[] methods = psiClass.getMethods();
                if (methods.length > 0) {
                    sb.append("  // === 方法声明 ===\n");
                    for (PsiMethod method : methods) {
                        sb.append("  ");
                        if (method.hasModifierProperty("public")) sb.append("public ");
                        if (method.hasModifierProperty("private")) sb.append("private ");
                        if (method.hasModifierProperty("protected")) sb.append("protected ");
                        if (method.hasModifierProperty("static")) sb.append("static ");

                        if (method.getReturnType() != null) {
                            sb.append(method.getReturnType().getPresentableText()).append(" ");
                        }
                        sb.append(method.getName()).append("(");

                        PsiParameter[] params = method.getParameterList().getParameters();
                        for (int i = 0; i < params.length; i++) {
                            sb.append(params[i].getType().getPresentableText()).append(" ").append(params[i].getName());
                            if (i < params.length - 1) sb.append(", ");
                        }
                        sb.append(");\n");
                    }
                }

                sb.append("}\n");
                return sb.toString();
            } catch (Exception e) {
                // Ignore errors
            }
            return null;
        });
    }

    public static String resolveSource(Project project, String fullyQualifiedMethodName) {
        return ReadAction.compute(() -> {
            try {
                int paramStart = fullyQualifiedMethodName.indexOf('(');
                String signatureWithoutParams = fullyQualifiedMethodName;
                String[] requestedParamTypes = new String[0];

                if (paramStart != -1 && fullyQualifiedMethodName.endsWith(")")) {
                    signatureWithoutParams = fullyQualifiedMethodName.substring(0, paramStart);
                    String paramsStr = fullyQualifiedMethodName.substring(paramStart + 1, fullyQualifiedMethodName.length() - 1);
                    if (!paramsStr.isEmpty()) {
                        requestedParamTypes = paramsStr.split("\\s*,\\s*");
                    }
                }

                int lastDot = signatureWithoutParams.lastIndexOf('.');
                if (lastDot == -1) return null;

                String className = signatureWithoutParams.substring(0, lastDot);
                String methodName = signatureWithoutParams.substring(lastDot + 1);

                JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
                // Use allScope to allow finding methods in libraries/JDK if source is downloaded
                PsiClass psiClass = facade.findClass(className, GlobalSearchScope.allScope(project));

                if (psiClass == null) return null;

                PsiMethod[] methods = psiClass.findMethodsByName(methodName, true);
                PsiMethod targetMethod = null;

                // Match exact method by parameter count/types
                for (PsiMethod method : methods) {
                    PsiParameter[] actualParams = method.getParameterList().getParameters();
                    if (actualParams.length == requestedParamTypes.length) {
                        boolean match = true;
                        for (int i = 0; i < actualParams.length; i++) {
                            // Simple substring match to handle generics or simple names gracefully
                            if (!actualParams[i].getType().getCanonicalText().contains(requestedParamTypes[i]) &&
                                !requestedParamTypes[i].contains(actualParams[i].getType().getPresentableText())) {
                                match = false;
                                break;
                            }
                        }
                        if (match) {
                            targetMethod = method;
                            break;
                        }
                    }
                }

                // Fallback to first method if parameter matching fails but name exists
                if (targetMethod == null && methods.length > 0) {
                    targetMethod = methods[0];
                }

                if (targetMethod != null) {
                    PsiClass containingClass = targetMethod.getContainingClass();
                    if (containingClass != null && (containingClass.isInterface() || targetMethod.hasModifierProperty("abstract"))) {
                        // Interface or abstract method -> find implementations
                        Query<PsiElement> query = DefinitionsScopedSearch.search(targetMethod).allowParallelProcessing();
                        List<PsiMethod> implementations = new ArrayList<>();
                        for (PsiElement element : query) {
                            if (element instanceof PsiMethod) {
                                implementations.add((PsiMethod) element);
                            }
                        }

                        if (implementations.size() == 1) {
                            return implementations.get(0).getText();
                        } else if (implementations.size() > 1) {
                            StringBuilder sb = new StringBuilder();
                            sb.append("// 注意：该接口方法有 ").append(implementations.size()).append(" 个实现类。以下是部分实现的源码：\n");
                            for (int i = 0; i < Math.min(3, implementations.size()); i++) {
                                PsiClass implClass = implementations.get(i).getContainingClass();
                                sb.append("// --- 实现类: ").append(implClass != null ? implClass.getQualifiedName() : "Unknown").append(" ---\n");
                                sb.append(implementations.get(i).getText()).append("\n\n");
                            }
                            return sb.toString();
                        }
                    }
                    return targetMethod.getText();
                }

            } catch (Exception e) {
                // Ignore parsing errors
            }
            return null;
        });
    }

}
