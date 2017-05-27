package se.tedro.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import org.junit.Before;
import org.junit.Test;
import se.tedro.tests.api.v1.EnumTest;
import se.tedro.tests.api.v1.InterfaceTest;
import se.tedro.tests.api.v1.TupleTest;
import se.tedro.tests.api.v1.TypeTest;

public class ApiTest {
  private ObjectMapper m;

  @Before
  public void setup() {
    m = new ObjectMapper();
    m.registerModule(new Jdk8Module());
  }

  @Test
  public void testEnum() throws Exception {
    assertEquals(EnumTest.ONE, m.readValue("\"one\"", EnumTest.class));
    assertEquals(EnumTest.TWO, m.readValue("\"two\"", EnumTest.class));
  }

  @Test(expected = Exception.class)
  public void testBadEnum() throws Exception {
    m.readValue("\"three\"", EnumTest.class);
  }

  @Test
  public void testTuple() throws Exception {
    final String reference = "[42,\"foo\"]";
    final TupleTest tuple = m.readValue(reference, TupleTest.class);
    assertEquals(42, tuple.getTimestamp());
    assertEquals("foo", tuple.getValue());
    assertEquals(reference, m.writeValueAsString(tuple));
  }

  @Test
  public void testInterface() throws Exception {
    final String reference = "{\"type\":\"absolute\",\"start\":0,\"end\":1024}";
    final InterfaceTest test = m.readValue(reference, InterfaceTest.class);
    assertTrue(test instanceof InterfaceTest.Absolute);
    final InterfaceTest.Absolute absolute = (InterfaceTest.Absolute) test;
    assertEquals(0, absolute.getStart());
    assertEquals(1024, absolute.getEnd());
    assertEquals(reference, m.writeValueAsString(absolute));
  }

  @Test
  public void testType() throws Exception {
    final String reference = "{\"number\":\"one\"}";
    final TypeTest test = m.readValue(reference, TypeTest.class);
    assertEquals(EnumTest.ONE, test.getNumber());
    assertEquals(reference, m.writeValueAsString(test));
  }
}
