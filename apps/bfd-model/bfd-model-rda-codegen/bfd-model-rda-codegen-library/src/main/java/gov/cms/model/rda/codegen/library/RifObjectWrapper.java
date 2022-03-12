package gov.cms.model.rda.codegen.library;

import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Objects;
import org.apache.commons.csv.CSVRecord;

public class RifObjectWrapper {
  private final ImmutableList<RifLineWrapper> lines;
  private final RifLineWrapper header;

  public RifObjectWrapper(List<CSVRecord> csvRecords) {
    // Verify the inputs.
    Objects.requireNonNull(csvRecords);
    if (csvRecords.isEmpty()) {
      throw new IllegalArgumentException();
    }
    lines = csvRecords.stream().map(RifLineWrapper::new).collect(ImmutableList.toImmutableList());
    header = lines.get(0);
  }

  public RifObjectWrapper(RifLineWrapper singleLine) {
    lines = ImmutableList.of(singleLine);
    header = singleLine;
  }

  /**
   * Gets the header line.
   *
   * @return Header line from the RIF record.
   */
  public RifLineWrapper getHeader() {
    return header;
  }

  public int getLinesCount() {
    return lines.size();
  }

  public RifObjectWrapper getLines(int index) {
    return new RifObjectWrapper(lines.get(index));
  }

  /**
   * Tests whether a value exists for {@link Enum}.
   *
   * @param e an enum
   * @return true if the value is non-null and non-empty
   */
  public boolean hasValue(final String label) {
    return header.hasValue(label);
  }

  /**
   * Returns a (possibly empty) value for {@link Enum}.
   *
   * @param e an enum
   * @return the String at the given enum String
   */
  public String getValue(final String label) {
    return header.getValue(label);
  }
}
