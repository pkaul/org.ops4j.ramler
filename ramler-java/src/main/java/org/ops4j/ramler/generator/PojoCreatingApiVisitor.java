/*
 * Copyright 2016 OPS4J Contributors
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

import org.ops4j.ramler.exc.Exceptions;
import org.raml.v2.api.model.v10.datamodel.ObjectTypeDeclaration;

import com.sun.codemodel.JClassAlreadyExistsException;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JPackage;

public class PojoCreatingApiVisitor implements ApiVisitor {

    private GeneratorContext context;

    private JPackage pkg;

    public PojoCreatingApiVisitor(GeneratorContext context) {
        this.context = context;
        this.pkg = context.getModelPackage();
    }

    @Override
    public void visitObjectTypeStart(ObjectTypeDeclaration type) {
        try {
            JDefinedClass klass = pkg._class(type.name());
            context.addType(type.type(), klass);
            context.annotateAsGenerated(klass);
        }
        catch (JClassAlreadyExistsException exc) {
            throw Exceptions.unchecked(exc);
        }
    }
}