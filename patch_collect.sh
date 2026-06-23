for file in ExplainCodeAction ReviewCodeAction GenerateUnitTestAction GenerateJavaDocAction; do
  sed -i 's/String collectedCode = collectCodeFromCallStack/String collectedCode = MethodUtils.collectCodeFromCallStack/g' src/main/java/com/huq/idea/flow/apidoc/$file.java
done
