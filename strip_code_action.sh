for file in ExplainCodeAction ReviewCodeAction GenerateUnitTestAction GenerateJavaDocAction; do
  sed -i '/private String collectCodeFromCallStack(CallStack callStack) {/,/^    }/d' src/main/java/com/huq/idea/flow/apidoc/$file.java
  sed -i '/private void collectCodeFromChildCallStack(StringBuilder codeBuilder, CallStack callStack, int depth) {/,/^    }/d' src/main/java/com/huq/idea/flow/apidoc/$file.java
  sed -i '/private void appendMethodCode(StringBuilder codeBuilder, CallStack callStack) {/,/^    }/d' src/main/java/com/huq/idea/flow/apidoc/$file.java
done
