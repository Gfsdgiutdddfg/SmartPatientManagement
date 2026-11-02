package com.smartpatient;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class ProjectSmokeTest {
 @Test void applicationEntryPointExists(){assertNotNull(Main.class);}
 @Test void roleNamesAreExpected(){assertTrue(java.util.Set.of("PATIENT","DOCTOR","ADMIN").contains("PATIENT"));}
}
