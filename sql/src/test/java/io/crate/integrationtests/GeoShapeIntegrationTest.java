/*
 * Licensed to Crate under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.  Crate licenses this file
 * to you under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * However, if you have executed another commercial license agreement
 * with Crate these terms will supersede the license and you may use the
 * software solely pursuant to the terms of the relevant commercial
 * agreement.
 */

package io.crate.integrationtests;

import com.google.common.collect.ImmutableMap;
import io.crate.testing.TestingHelpers;
import io.crate.types.DataType;
import io.crate.types.DataTypes;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;

import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.core.Is.is;

public class GeoShapeIntegrationTest extends SQLTransportIntegrationTest {

    @Before
    public void prepare() throws Exception {
        execute("CREATE TABLE shaped (" +
                "  id long primary key," +
                "  shape geo_shape" +
                ") with (number_of_replicas=0)");
        ensureGreen();
        execute("INSERT INTO shaped (id, shape) VALUES (?, ?)", $$(
                $(1L, "POINT (13.0 52.4)"),
                $(42L, ImmutableMap.of(
                        "type", "LineString",
                        "coordinates", new double[][] {
                                {0, 0},
                                {1, 1}
                        }
                ))
        ));
        execute("REFRESH TABLE shaped");
    }

    @Test
    public void testSelectGeoShapeFromSource() throws Exception {
        execute("select shape from shaped order by id");
        assertThat(TestingHelpers.printedTable(response.rows()), is(
                "{coordinates=[13.0, 52.4], type=Point}\n" +
                "{coordinates=[[0.0, 0.0], [1.0, 1.0]], type=LineString}\n"));
        assertThat(response.columnTypes()[0], is((DataType)DataTypes.GEO_SHAPE));
        assertThat(response.rows()[0][0], instanceOf(Map.class));
    }
}
