/*
 * Licensed to CRATE Technology GmbH ("Crate") under one or more contributor
 * license agreements.  See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.  Crate licenses
 * this file to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * However, if you have executed another commercial license agreement
 * with Crate these terms will supersede the license and you may use the
 * software solely pursuant to the terms of the relevant commercial agreement.
 */

package io.crate.operation.reference.doc.lucene;


import io.crate.operation.reference.doc.ColumnReferenceExpression;

public abstract class ColumnReferenceCollectorExpression<ReturnType> extends
        LuceneCollectorExpression<ReturnType> implements ColumnReferenceExpression {

    protected final String columnName;

    public ColumnReferenceCollectorExpression(String columnName) {
        this.columnName = columnName;
    }

    public String columnName() {
        return columnName;
    }

    @Override
    public String toString() {
        return columnName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ColumnReferenceCollectorExpression)) return false;

        ColumnReferenceCollectorExpression that = (ColumnReferenceCollectorExpression) o;

        if (!columnName.equals(that.columnName)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return columnName.hashCode();
    }
}
