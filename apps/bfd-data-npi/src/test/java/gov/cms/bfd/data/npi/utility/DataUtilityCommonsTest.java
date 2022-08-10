package gov.cms.bfd.data.npi.utility;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.any;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

/** Data Utility Commons test class. */
public final class DataUtilityCommonsTest {

  /** Throws Exception when file is not a directory. */
  @Test
  public void getFDADrugCodesThrowsExceptionWhenFileIsNotADirectory() {
    String outputDir = "../temp/";
    Path tempDir = Paths.get(outputDir);
    ;
    try (MockedStatic<Paths> paths = Mockito.mockStatic(Paths.class)) {
      try (MockedStatic<Path> path = Mockito.mockStatic(Path.class)) {
        try (MockedStatic<Files> files = Mockito.mockStatic(Files.class)) {

          paths.when(() -> Paths.get(any())).thenAnswer((Answer<Path>) invocation -> tempDir);
          files
              .when(() -> Files.isDirectory(any()))
              .thenAnswer((Answer<Boolean>) invocation -> false);
          Throwable exception =
              assertThrows(
                  IllegalStateException.class,
                  () -> {
                    DataUtilityCommons.getNPIOrgNames(outputDir, any());
                  });
          assertEquals("OUTPUT_DIR does not exist for NPI download.", exception.getMessage());
        }
      }
    }
  }
}
