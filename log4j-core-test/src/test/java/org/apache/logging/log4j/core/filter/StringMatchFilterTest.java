package org.apache.logging.log4j.core.filter;

import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.test.junit.LoggerContextSource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit test for {@link StringMatchFilter}.
 */
public class StringMatchFilterTest {

  /**
   * Test that if no match-string is set on the builder, the '{@link StringMatchFilter.Builder#build()}' returns
   * {@code null}.
   */
  @Test
  public void testFilterBuilderFailsWithNullText() {
    Assertions.assertNull(StringMatchFilter.newBuilder().build());
  }

  /**
   * Test that if a {@code null} string is set as a match-pattern, an {@code IllegalArgumentExeption} is thrown.
   */
  @Test
  void testFilterBuilderFailsWithExceptionOnNullText() {
    Assertions.assertThrows(IllegalArgumentException.class, () -> StringMatchFilter.newBuilder().setMatchString(null));
  }

  /**
   * Test that if an empty ({@code ""}) string is set as a match-pattern, an {@code IllegalArgumentException} is thrown.
   */
  @Test
  void testFilterBuilderFailsWithExceptionOnEmptyText() {
    Assertions.assertThrows(IllegalArgumentException.class, () -> StringMatchFilter.newBuilder().setMatchString(""));
  }

  /**
   * Test that if a {@link StringMatchFilter} is specified with a 'text' attribute it is correctly instantiated.
   *
   * @param configuration the configuration
   */
  @Test
  @LoggerContextSource("log4j2-stringmatchfilter-3153-ok.xml")
  void testConfigurationWithTextPOS(final Configuration configuration) {
    final Filter filter = configuration.getFilter();
    assertNotNull(filter, "The filter should not be null.");
    assertInstanceOf(StringMatchFilter.class, filter, "Expected a StringMatchFilter, but got: " + filter.getClass());
    assertEquals("FooBar", filter.toString());
  }

  /**
   * Test that if a {@link StringMatchFilter} is specified without a 'text' attribute it is not instantiated.
   *
   * @param configuration the configuration
   */
  @Test
  @LoggerContextSource("log4j2-stringmatchfilter-3153-nok.xml")
  void testConfigurationWithTextNEG(final Configuration configuration) {
    final Filter filter = configuration.getFilter();
    assertNull(filter, "The filter should be null.");
  }

}
