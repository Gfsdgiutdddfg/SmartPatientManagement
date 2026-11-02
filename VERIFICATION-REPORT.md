# JUnit test compilation fix

## Error shown
`AccountWorkflowTest.java:[92,11] implicitly declared classes are not supported in -source 17`
`AccountWorkflowTest.java:[1,1] compact source file should not have package declaration`

## Root cause
The new `specializationsSchemaHasNameColumnAfterInitialization()` test method had been appended after the closing brace of `AccountWorkflowTest`. Java therefore treated the method as an illegal top-level declaration.

## Fix
Moved the schema regression test inside the `AccountWorkflowTest` class, before its final closing brace. The file retains its package declaration and Java 17-compatible class structure.

## Checks
- Test source structural check: PASS (test method is inside `AccountWorkflowTest`).
- `pom.xml` XML parse: PASS.
- `launch.command` shell syntax: PASS.
- ZIP integrity: PASS.
- Maven `clean test`: NOT RUN in this environment; Maven is not installed.
- Full Java compilation against actual dependencies: NOT RUN.

## Run on Mac
From the extracted project root:
```bash
mvn clean test
mvn clean verify
mvn clean package
```
