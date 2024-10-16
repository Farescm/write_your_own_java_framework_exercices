package com.github.forax.framework.orm;

import org.h2.jdbcx.JdbcDataSource;

import javax.sql.DataSource;
import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.Serial;
import java.lang.reflect.Constructor;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ORM {

  static final String DEFAULT_TYPE = "VARCHAR(255)";

  private ORM() {
    throw new AssertionError();
  }

  private static final ThreadLocal<Connection> CONNECTION_LOCAL = new ThreadLocal<>(); /* par défaut valeur = null */

  public static void transaction(JdbcDataSource dataSource, TransactionBlock block) throws SQLException {
    Objects.requireNonNull(dataSource);
    Objects.requireNonNull(block);
    try (var connection = dataSource.getConnection()) {
      connection.setAutoCommit(false);
      CONNECTION_LOCAL.set(connection);
      try {
        block.run();
      } catch (Exception e) {
        connection.rollback();
        throw e;
      } finally {
        CONNECTION_LOCAL.remove();
      }
      connection.commit();
    } // connection.close()

  }

  public static Connection currentConnection() {
    var connection = CONNECTION_LOCAL.get();
    if (connection == null) {
      throw new IllegalStateException("no transaction available");
    }
    return connection;
  }

  static String findTableName(Class<?> beanClass) {
    var tableAnnotation = beanClass.getAnnotation(Table.class); /* Récupere la valeur de l'annotation Table */
//    if(tableAnnotation != null){
//      return tableAnnotation.value();
//    }
//    return beanClass.getName().toUpperCase(Locale.ROOT);

    var name = tableAnnotation == null ? beanClass.getSimpleName() : tableAnnotation.value();
    return name.toUpperCase(Locale.ROOT);
  }

  static String findColumnName(PropertyDescriptor property) {
    var getter = property.getReadMethod(); /* Recuperer la method de lecture */
    var column = getter.getAnnotation(Column.class);
    if (column != null) {
      return column.value();
    }
    return property.getName().toUpperCase(Locale.ROOT);
  }

  private static boolean isPrimaryKey(PropertyDescriptor property) {
    var getter = property.getReadMethod();
//    if(getter == null){
//      return false;
//    }
//    return getter.isAnnotationPresent(Id.class);

    return getter != null && getter.isAnnotationPresent(Id.class);
  }

  public static void createTable(Class<?> beanClass) throws SQLException {
    Objects.requireNonNull(beanClass);

    var builder = new StringBuilder();

    builder.append("CREATE TABLE ")
            .append(findTableName(beanClass))
            .append(" (\n");
    var beanInfo = Utils.beanInfo(beanClass);
    var separator = "";
    String primaryColumn = null;
    for (var property : beanInfo.getPropertyDescriptors()) {
      if (property.getName().equals("class")) { /* pas garder le class */
        continue;
      }
      var column = findColumnName(property);
      if (isPrimaryKey(property)) {
        primaryColumn = column;
      }
      var propertyType = property.getPropertyType();
      var type = TYPE_MAPPING.getOrDefault(propertyType, DEFAULT_TYPE);

      var nullConstraint = propertyType.isPrimitive() ? " NOT NULL" : "";

      builder.append(separator).append(column).append(' ').append(type).append(nullConstraint);

      separator = ",\n";

      if (primaryColumn != null) {
        builder.append(separator).append("PRIMARY KEY (").append(primaryColumn).append(")\n");
      }
      builder.append(");");

      var connection = currentConnection();
      try (Statement statement = connection.createStatement()) {
        System.out.println(builder.toString());
        var query = builder.toString();
        statement.executeUpdate(query);
      }
      connection.commit();
    }
  }

  static String createSaveQuery(String tableName, BeanInfo beanInfo) {
    var properties = beanInfo.getPropertyDescriptors();
    return "INSERT INTO " + tableName + " " +
            Arrays.stream(beanInfo.getPropertyDescriptors())
                    .filter(property -> !property.getName().equals("class"))
                    .map(ORM::findColumnName)
                    .collect(Collectors.joining(", ", "(", ")"))
            + " VALUES (" + String.join(", " + String.join(", ", Collections.nCopies(properties.length - 1, "?"))
            + ");");
  }

  public static Object save(Connection connection, String tableName, BeanInfo beanInfo, Object bean, PropertyDescriptor idProperty) throws SQLException{
    var sqlQuery = createSaveQuery(tableName, beanInfo);
    try(var statement = connection.prepareStatement(sqlQuery)){
      var index = 1;
      for(var property : beanInfo.getPropertyDescriptors()){
        if(property.getName().equals("class")){
          continue;
        }
        var getter = property.getReadMethod();
        var value = Utils.invokeMethod(bean, getter);
        statement.setObject(index++, value);
      }
      statement.executeUpdate();
      if(idProperty != null){
        try(var resultSet = statement.getGeneratedKeys()){
          if(resultSet.next()){

          }
        }
      }

    }
  }

  public static <R extends Repository<?, ?>> R createRepository(Class<R> repositoryType) {
    var beanType = findBeanTypeFromRepository(repositoryType);
    var tableName = findTableName(beanType);
    var beanInfo = Utils.beanInfo(beanType);
    var constructor = Utils.defaultConstructor(beanType);
//    var idProperty =

    return repositoryType.cast(Proxy.newProxyInstance(repositoryType.getClassLoader(),
            new Class<?>[]{repositoryType},
            ((proxy, method, args) -> {
              var connection = currentConnection();
              try {
                return switch (method.getName()){
                  case "findAll" -> findAll(connection, "SELECT * FROM " + tableName, beanInfo, constructor);
                  case "save" -> save(connection, tableName, beanInfo, args[0], idProperty);
                  case "findById" -> findAll(connection, "SELECT * FROM " + tableName + " WHERE " + , beanInfo, constructor);
                }
              }
            })
    ))
  }

  private static List<?> findAll(Connection connection, String sqlQuery ,Class<?> beanInfo, Class<?> beanType, Constructor<?> constructor) throws SQLException {
    var tableName = findTableName(beanType);
    var sqlQuery = "SELECT * FROM " + tableName;
    try (PreparedStatement statement = connection.prepareStatement(sqlQuery)) {
      try (ResultSet resultSet = statement.executeQuery()) {
        while (resultSet.next()) {
          var bean = Utils.newInstance(constructor);
          var index = 1;
          for (var property : beanInfo.getP)
        }
      }
    }
  }

  @FunctionalInterface
  public interface TransactionBlock {
    void run() throws SQLException;
  }

  private static final Map<Class<?>, String> TYPE_MAPPING = Map.of(
          int.class, "INTEGER",
          Integer.class, "INTEGER",
          long.class, "BIGINT",
          Long.class, "BIGINT",
          String.class, "VARCHAR(255)"
  );

  private static Class<?> findBeanTypeFromRepository(Class<?> repositoryType) {
    var repositorySupertype = Arrays.stream(repositoryType.getGenericInterfaces())
            .flatMap(superInterface -> {
              if (superInterface instanceof ParameterizedType parameterizedType
                      && parameterizedType.getRawType() == Repository.class) {
                return Stream.of(parameterizedType);
              }
              return null;
            })
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("invalid repository interface " + repositoryType.getName()));
    var typeArgument = repositorySupertype.getActualTypeArguments()[0];
    if (typeArgument instanceof Class<?> beanType) {
      return beanType;
    }
    throw new IllegalArgumentException("invalid type argument " + typeArgument + " for repository interface " + repositoryType.getName());
  }

  private static class UncheckedSQLException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 42L;

    private UncheckedSQLException(SQLException cause) {
      super(cause);
    }

    @Override
    public SQLException getCause() {
      return (SQLException) super.getCause();
    }
  }


  // --- do not change the code above

  /* utilisé une lambda pour l'encapsuler et pété une exception */

}
