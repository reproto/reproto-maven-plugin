package se.tedro.tests;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import se.tedro.tests.api.v1.Date;
import se.tedro.tests.api.v1.Query;

public class App {
  public static void main(String[] args) throws Exception {
    final Query.Builder builder = new Query.Builder();

    builder.date(new Date.Absolute(0, 10));

    final Query query = builder.build();

    final ObjectMapper m = new ObjectMapper();
    m.registerModule(new Jdk8Module());

    final String out = m.writeValueAsString(query);

    System.out.println(out);
    System.out.println(m.readValue(out, Query.class));
  }
}
