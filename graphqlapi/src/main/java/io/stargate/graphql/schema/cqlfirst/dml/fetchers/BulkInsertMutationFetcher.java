/*
 * Copyright The Stargate Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.stargate.graphql.schema.cqlfirst.dml.fetchers;

import static io.stargate.graphql.schema.cqlfirst.dml.fetchers.TtlFromOptionsExtractor.getTTL;

import graphql.schema.DataFetchingEnvironment;
import io.stargate.auth.*;
import io.stargate.db.datastore.DataStore;
import io.stargate.db.datastore.DataStoreFactory;
import io.stargate.db.query.BoundDMLQuery;
import io.stargate.db.query.BoundQuery;
import io.stargate.db.query.builder.ValueModifier;
import io.stargate.db.schema.Column;
import io.stargate.db.schema.Table;
import io.stargate.graphql.schema.cqlfirst.dml.NameMapping;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BulkInsertMutationFetcher extends BulkMutationFetcher {

  public BulkInsertMutationFetcher(
      Table table,
      NameMapping nameMapping,
      AuthorizationService authorizationService,
      DataStoreFactory dataStoreFactory) {
    super(table, nameMapping, authorizationService, dataStoreFactory);
  }

  @Override
  protected List<BoundQuery> buildQueries(
      DataFetchingEnvironment environment,
      DataStore dataStore,
      AuthenticationSubject authenticationSubject)
      throws UnauthorizedException {
    boolean ifNotExists =
        environment.containsArgument("ifNotExists")
            && environment.getArgument("ifNotExists") != null
            && (Boolean) environment.getArgument("ifNotExists");

    List<Map<String, Object>> valuesToInsert = environment.getArgument("values");
    List<BoundQuery> boundQueries = new ArrayList<>(valuesToInsert.size());
    for (Map<String, Object> value : valuesToInsert) {
      BoundQuery query =
          dataStore
              .queryBuilder()
              .insertInto(table.keyspace(), table.name())
              .value(buildInsertValues(value))
              .ifNotExists(ifNotExists)
              .ttl(getTTL(environment))
              .build()
              .bind();

      authorizationService.authorizeDataWrite(
          authenticationSubject,
          table.keyspace(),
          table.name(),
          TypedKeyValue.forDML((BoundDMLQuery) query),
          Scope.MODIFY,
          SourceAPI.GRAPHQL);
      boundQueries.add(query);
    }
    return boundQueries;
  }

  private List<ValueModifier> buildInsertValues(Map<String, Object> value) {

    List<ValueModifier> modifiers = new ArrayList<>();
    for (Map.Entry<String, Object> entry : value.entrySet()) {
      Column column = dbColumnGetter.getColumn(table, entry.getKey());
      modifiers.add(ValueModifier.set(column.name(), toDBValue(column, entry.getValue())));
    }
    return modifiers;
  }
}
