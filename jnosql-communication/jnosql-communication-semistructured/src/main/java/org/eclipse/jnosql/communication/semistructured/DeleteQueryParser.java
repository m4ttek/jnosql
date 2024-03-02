/*
 *
 *  Copyright (c) 2022 Contributors to the Eclipse Foundation
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   and Apache License v2.0 which accompanies this distribution.
 *   The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html
 *   and the Apache License v2.0 is available at http://www.opensource.org/licenses/apache2.0.php.
 *
 *   You may elect to redistribute this code under either of these licenses.
 *
 *   Contributors:
 *
 *   Otavio Santana
 *
 */
package org.eclipse.jnosql.communication.semistructured;


import org.eclipse.jnosql.communication.Params;
import org.eclipse.jnosql.communication.QueryException;
import org.eclipse.jnosql.communication.query.DeleteQueryConverter;

import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

public final class DeleteQueryParser implements BiFunction<org.eclipse.jnosql.communication.query.DeleteQuery, ColumnObserverParser, ColumnDeleteQueryParams> {



    Stream<CommunicationEntity> query(String query, DatabaseManager manager, ColumnObserverParser observer) {

        DeleteQuery deleteQuery = getQuery(query, observer);
        manager.delete(deleteQuery);
        return Stream.empty();
    }


    CommunicationPreparedStatement prepare(String query, DatabaseManager manager,
                                           ColumnObserverParser observer) {
        Params params = Params.newParams();
        DeleteQuery deleteQuery = getQuery(query, params, observer);
        return CommunicationPreparedStatement.delete(deleteQuery, params, query, manager);
    }



    @Override
    public ColumnDeleteQueryParams apply(org.eclipse.jnosql.communication.query.DeleteQuery deleteQuery,
                                         ColumnObserverParser columnObserverParser) {

        requireNonNull(deleteQuery, "deleteQuery is required");
        requireNonNull(columnObserverParser, "columnObserverParser is required");
        Params params = Params.newParams();
        DeleteQuery query = getQuery(params, columnObserverParser, deleteQuery);
        return new ColumnDeleteQueryParams(query, params);
    }

    private DeleteQuery getQuery(String query, Params params, ColumnObserverParser observer) {
        DeleteQueryConverter converter = new DeleteQueryConverter();
        org.eclipse.jnosql.communication.query.DeleteQuery deleteQuery = converter.apply(query);

        return getQuery(params, observer, deleteQuery);
    }

    private DeleteQuery getQuery(Params params, ColumnObserverParser observer, org.eclipse.jnosql.communication.query.DeleteQuery deleteQuery) {
        String columnFamily = observer.fireEntity(deleteQuery.entity());
        List<String> columns = deleteQuery.fields().stream()
                .map(f -> observer.fireField(columnFamily, f))
                .collect(Collectors.toList());
        CriteriaCondition condition = deleteQuery.where().map(c -> Conditions.getCondition(c, params, observer, columnFamily))
                .orElse(null);

        return new DefaultDeleteQuery(columnFamily, condition, columns);
    }

    private DeleteQuery getQuery(String query, ColumnObserverParser observer) {

        DeleteQueryConverter converter = new DeleteQueryConverter();
        org.eclipse.jnosql.communication.query.DeleteQuery deleteQuery = converter.apply(query);

        String columnFamily = observer.fireEntity(deleteQuery.entity());
        List<String> columns = deleteQuery.fields().stream()
                .map(f -> observer.fireField(columnFamily, f))
                .collect(Collectors.toList());
        Params params = Params.newParams();

        CriteriaCondition condition = deleteQuery.where()
                .map(c -> Conditions.getCondition(c, params, observer, columnFamily)).orElse(null);

        if (params.isNotEmpty()) {
            throw new QueryException("To run a query with a parameter use a PrepareStatement instead.");
        }
        return new DefaultDeleteQuery(columnFamily, condition, columns);
    }
}