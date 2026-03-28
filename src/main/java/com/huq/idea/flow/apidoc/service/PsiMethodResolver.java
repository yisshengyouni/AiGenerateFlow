package com.huq.idea.flow.apidoc.service;

import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.openapi.application.ReadAction;

public class PsiMethodResolver {

    /**
     * Attempts to find a method's source code by its fully qualified signature.
     * Expects format like: "com.example.service.UserService.getUserById" or "com.example.service.UserService.getUserById(java.lang.String)"
     */
    public static String resolveMethodSource(Project project, String fullyQualifiedMethodName) {
        return ReadAction.compute(() -> {
            try {
                // Remove parameter info if present for simple matching
                int paramStart = fullyQualifiedMethodName.indexOf('(');
                String cleanSignature = fullyQualifiedMethodName;
                if (paramStart != -1) {
                    cleanSignature = fullyQualifiedMethodName.substring(0, paramStart);
                }

                int lastDot = cleanSignature.lastIndexOf('.');
                if (lastDot == -1) return null;

                String className = cleanSignature.substring(0, lastDot);
                String methodName = cleanSignature.substring(lastDot + 1);

                JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
                // Search only in project scope to avoid pulling in JDK/Library sources unnecessarily
                PsiClass psiClass = facade.findClass(className, GlobalSearchScope.projectScope(project));

                if (psiClass == null) return null;

                PsiMethod[] methods = psiClass.findMethodsByName(methodName, true);

                if (methods.length > 0) {
                    // Very simple resolution: just take the first one found matching the name.
                    // A more robust implementation would match parameter types too.
                    return methods[0].getText();
                }

            } catch (Exception e) {
                // Ignore parsing errors
            }
            return null;
        });
    }
}
