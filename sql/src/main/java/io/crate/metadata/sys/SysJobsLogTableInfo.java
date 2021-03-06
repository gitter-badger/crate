/*
 * Licensed to CRATE Technology GmbH ("Crate") under one or more contributor
 * license agreements.  See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.  Crate licenses
 * this file to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

package io.crate.metadata.sys;

import com.google.common.collect.ImmutableList;
import io.crate.analyze.WhereClause;
import io.crate.metadata.*;
import io.crate.types.DataType;
import io.crate.types.DataTypes;
import org.elasticsearch.cluster.ClusterService;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.inject.Singleton;

import javax.annotation.Nullable;
import java.util.*;

@Singleton
public class SysJobsLogTableInfo extends SysTableInfo {

    public static final TableIdent IDENT = new TableIdent(SCHEMA, "jobs_log");

    private final Map<ColumnIdent, ReferenceInfo> infos = new LinkedHashMap<>();
    private final LinkedHashSet<ReferenceInfo> columns = new LinkedHashSet<>();
    private final List<ColumnIdent> primaryKeys = ImmutableList.of(new ColumnIdent("id"));

    public static class Columns {
        public static final ColumnIdent ID = new ColumnIdent("id");
        public static final ColumnIdent STMT = new ColumnIdent("stmt");
        public static final ColumnIdent STARTED = new ColumnIdent("started");
        public static final ColumnIdent ENDED = new ColumnIdent("ended");
        public static final ColumnIdent ERROR = new ColumnIdent("error");
    }


    @Inject
    public SysJobsLogTableInfo(ClusterService clusterService, SysSchemaInfo sysSchemaInfo) {
        super(clusterService, sysSchemaInfo);

        register(Columns.ID, DataTypes.STRING);
        register(Columns.STMT, DataTypes.STRING);
        register(Columns.STARTED, DataTypes.TIMESTAMP);
        register(Columns.ENDED, DataTypes.TIMESTAMP);
        register(Columns.ERROR, DataTypes.STRING);
    }

    private void register(ColumnIdent column, DataType type) {
        ReferenceInfo info = new ReferenceInfo(new ReferenceIdent(IDENT, column), RowGranularity.DOC, type);
        if (column.isColumn()) {
            columns.add(info);
        }
        infos.put(column, info);
    }

    @Nullable
    @Override
    public ReferenceInfo getReferenceInfo(ColumnIdent columnIdent) {
        return infos.get(columnIdent);
    }

    @Override
    public Collection<ReferenceInfo> columns() {
        return columns;
    }

    @Override
    public RowGranularity rowGranularity() {
        return RowGranularity.DOC;
    }

    @Override
    public TableIdent ident() {
        return IDENT;
    }

    @Override
    public Routing getRouting(WhereClause whereClause, @Nullable String preference) {
        return tableRouting(whereClause);
    }

    @Override
    public List<ColumnIdent> primaryKey() {
        return primaryKeys;
    }

    @Override
    public Iterator<ReferenceInfo> iterator() {
        return infos.values().iterator();
    }
}
