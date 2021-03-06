/*
 * Copyright 2017 OPS4J Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ops4j.ramler.generator;

import org.junit.Test;

public class AdditionalPropertiesTest extends AbstractGeneratorTest {

    @Override
    public String getBasename() {
        return "addProp";
    }

    @Test
    public void shouldFindModelClasses() {
        assertClasses("Person");
    }

    @Test
    public void shouldFindPersonListMembers() {
        expectClass("Person");
        assertProperty(klass, "firstName", "String", "getFirstName", "setFirstName");
        assertProperty(klass, "lastName", "String", "getLastName", "setLastName");
        assertProperty(klass, "addProps", "Map<String,Object>", "getAddProps", "setAddProps");
        verifyClass();
    }
}
