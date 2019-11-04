/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.druid.indexing.overlord.autoscaling.gce;

import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.InjectableValues;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.druid.indexing.overlord.autoscaling.AutoScaler;
import org.apache.druid.jackson.DefaultObjectMapper;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Locale;

/**
 */
public class GCEAutoScalerTest
{
  @Before
  public void setUp()
  {
  }

  @After
  public void tearDown()
  {
  }

  private static void verifyAutoScaler(final GCEAutoScaler autoScaler)
  {
    Assert.assertEquals(1, autoScaler.getEnvConfig().getTargetWorkers());
    Assert.assertEquals(4, autoScaler.getMaxNumWorkers());
    Assert.assertEquals(2, autoScaler.getMinNumWorkers());
    Assert.assertEquals("winkie-country", autoScaler.getEnvConfig().getZoneName());
    Assert.assertEquals("super-project", autoScaler.getEnvConfig().getProjectId());
    Assert.assertEquals("druid-abc", autoScaler.getEnvConfig().getinstanceTemplate());
  }

  @Test
  public void testConfig()
  {
    final String json = "{\n"
            + "   \"envConfig\" : {\n"
            + "      \"targetWorkers\" : 1,\n"
            + "      \"maxWorkers\" : 4,\n"
            + "      \"minWorkers\" : 2,\n"
            + "      \"projectId\" : \"super-project\",\n"
            + "      \"zoneName\" : \"winkie-country\",\n"
            + "      \"instanceTemplate\" : \"druid-abc\"\n"
            + "   },\n"
            + "   \"type\" : \"gce\"\n"
            + "}";

    final ObjectMapper objectMapper = new DefaultObjectMapper()
            .registerModules((Iterable<Module>) new GCEModule().getJacksonModules());
    objectMapper.setInjectableValues(
          new InjectableValues()
          {
            @Override
            public Object findInjectableValue(
                    Object o,
                    DeserializationContext deserializationContext,
                    BeanProperty beanProperty,
                    Object o1
            )
            {
              return null;
            }
          }
    );

    try {
      final GCEAutoScaler autoScaler =
              (GCEAutoScaler) objectMapper.readValue(json, AutoScaler.class);
      verifyAutoScaler(autoScaler);

      final GCEAutoScaler roundTripAutoScaler = (GCEAutoScaler) objectMapper.readValue(
              objectMapper.writeValueAsBytes(autoScaler),
              AutoScaler.class
      );
      verifyAutoScaler(roundTripAutoScaler);

      Assert.assertEquals("Round trip equals", autoScaler, roundTripAutoScaler);
    }
    catch (Exception e) {
      Assert.fail(String.format(Locale.US, "Got exception in test %s", e.getMessage()));
    }
  }
}
