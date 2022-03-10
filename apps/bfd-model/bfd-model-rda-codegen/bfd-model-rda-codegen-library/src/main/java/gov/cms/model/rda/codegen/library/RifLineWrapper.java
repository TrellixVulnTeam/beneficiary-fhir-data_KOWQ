package gov.cms.model.rda.codegen.library;

import com.google.common.base.Strings;
import org.apache.commons.csv.CSVRecord;

public class RifLineWrapper {
  private final CSVRecord line;

  public RifLineWrapper(CSVRecord line) {
    this.line = line;
  }

  /**
   * Tests whether a value exists for {@link Enum}.
   *
   * @param e an enum
   * @return true if the value is non-null and non-empty
   */
  public boolean hasValue(final String label) {
    return !Strings.isNullOrEmpty(line.get(label));
  }

  /**
   * Returns a (possibly empty) value for {@link Enum}.
   *
   * @param e an enum
   * @return the String at the given enum String
   */
  public String getValue(final String label) {
    return line.get(label);
  }
}
