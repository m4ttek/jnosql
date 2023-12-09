/*
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
 */
package org.eclipse.jnosql.mapping.document.query;

import jakarta.data.repository.PageableRepository;
import jakarta.data.repository.Param;
import jakarta.data.repository.Query;
import jakarta.data.repository.Sort;
import jakarta.inject.Inject;
import jakarta.nosql.PreparedStatement;
import org.assertj.core.api.SoftAssertions;
import org.eclipse.jnosql.communication.Condition;
import org.eclipse.jnosql.communication.TypeReference;
import org.eclipse.jnosql.communication.Value;
import org.eclipse.jnosql.communication.document.Document;
import org.eclipse.jnosql.communication.document.DocumentCondition;
import org.eclipse.jnosql.communication.document.DocumentDeleteQuery;
import org.eclipse.jnosql.communication.document.DocumentQuery;
import org.eclipse.jnosql.mapping.core.Converters;
import org.eclipse.jnosql.mapping.document.DocumentEntityConverter;
import org.eclipse.jnosql.mapping.document.JNoSQLDocumentTemplate;
import org.eclipse.jnosql.mapping.document.MockProducer;
import org.eclipse.jnosql.mapping.document.entities.Person;
import org.eclipse.jnosql.mapping.document.entities.PersonStatisticRepository;
import org.eclipse.jnosql.mapping.document.entities.Vendor;
import org.eclipse.jnosql.mapping.document.spi.DocumentExtension;
import org.eclipse.jnosql.mapping.metadata.EntitiesMetadata;
import org.eclipse.jnosql.mapping.reflection.Reflections;
import org.eclipse.jnosql.mapping.spi.EntityMetadataExtension;
import org.jboss.weld.junit5.auto.AddExtensions;
import org.jboss.weld.junit5.auto.AddPackages;
import org.jboss.weld.junit5.auto.EnableAutoWeld;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.jnosql.communication.Condition.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@EnableAutoWeld
@AddPackages(value = {Converters.class, DocumentEntityConverter.class})
@AddPackages(MockProducer.class)
@AddPackages(Reflections.class)
@AddExtensions({EntityMetadataExtension.class, DocumentExtension.class})
class DocumentRepositoryProxyTest {

    private JNoSQLDocumentTemplate template;

    @Inject
    private EntitiesMetadata entities;

    @Inject
    private Converters converters;

    private PersonRepository personRepository;

    private VendorRepository vendorRepository;


    @BeforeEach
    void setUp() {
        this.template = Mockito.mock(JNoSQLDocumentTemplate.class);

        DocumentRepositoryProxy personHandler = new DocumentRepositoryProxy(template,
                entities, PersonRepository.class, converters);

        DocumentRepositoryProxy vendorHandler = new DocumentRepositoryProxy(template,
                entities, VendorRepository.class, converters);

        when(template.insert(any(Person.class))).thenReturn(Person.builder().build());
        when(template.insert(any(Person.class), any(Duration.class))).thenReturn(Person.builder().build());
        when(template.update(any(Person.class))).thenReturn(Person.builder().build());
        personRepository = (PersonRepository) Proxy.newProxyInstance(PersonRepository.class.getClassLoader(),
                new Class[]{PersonRepository.class},
                personHandler);
        vendorRepository = (VendorRepository) Proxy.newProxyInstance(VendorRepository.class.getClassLoader(),
                new Class[]{VendorRepository.class}, vendorHandler);
    }


    @Test
    void shouldSaveUsingInsertWhenDataDoesNotExist() {
        when(template.find(Person.class, 10L))
                .thenReturn(Optional.empty());


        ArgumentCaptor<Person> captor = ArgumentCaptor.forClass(Person.class);
        Person person = Person.builder().withName("Ada")
                .withId(10L)
                .withPhones(singletonList("123123"))
                .build();
        assertNotNull(personRepository.save(person));
        verify(template).insert(captor.capture());
        Person value = captor.getValue();
        assertEquals(person, value);
    }

    @Test
    void shouldSaveUsingUpdateWhenDataExists() {
        when(template.find(Person.class, 10L))
                .thenReturn(Optional.of(Person.builder().build()));

        ArgumentCaptor<Person> captor = ArgumentCaptor.forClass(Person.class);
        Person person = Person.builder().withName("Ada")
                .withId(10L)
                .withPhones(singletonList("123123"))
                .build();
        assertNotNull(personRepository.save(person));
        verify(template).update(captor.capture());
        Person value = captor.getValue();
        assertEquals(person, value);
    }


    @Test
    void shouldSaveIterable() {

        when(personRepository.findById(10L)).thenReturn(Optional.empty());

        when(template.singleResult(Mockito.any(DocumentQuery.class))).thenReturn(Optional.empty());

        ArgumentCaptor<Person> captor = ArgumentCaptor.forClass(Person.class);

        Person person = Person.builder().withName("Ada")
                .withId(10L)
                .withPhones(singletonList("123123"))
                .build();
        personRepository.saveAll(singletonList(person));
        verify(template).insert(captor.capture());
        verify(template).insert(captor.capture());
        Person personCapture = captor.getValue();
        assertEquals(person, personCapture);
    }


    @Test
    void shouldFindByNameInstance() {

        when(template.singleResult(Mockito.any(DocumentQuery.class))).thenReturn(Optional
                .of(Person.builder().build()));

        personRepository.findByName("name");

        ArgumentCaptor<DocumentQuery> captor = ArgumentCaptor.forClass(DocumentQuery.class);
        verify(template).singleResult(captor.capture());
        DocumentQuery query = captor.getValue();
        DocumentCondition condition = query.condition().get();
        assertEquals("Person", query.name());
        assertEquals(Condition.EQUALS, condition.condition());
        assertEquals(Document.of("name", "name"), condition.document());

        assertNotNull(personRepository.findByName("name"));
        when(template.singleResult(Mockito.any(DocumentQuery.class))).thenReturn(Optional
                .empty());

        assertNull(personRepository.findByName("name"));


    }

    @Test
    void shouldFindByNameANDAge() {
        Person ada = Person.builder()
                .withAge(20).withName("Ada").build();

        when(template.select(Mockito.any(DocumentQuery.class)))
                .thenReturn(Stream.of(ada));

        List<Person> persons = personRepository.findByNameAndAge("name", 20);
        ArgumentCaptor<DocumentQuery> captor = ArgumentCaptor.forClass(DocumentQuery.class);
        verify(template).select(captor.capture());
        assertThat(persons).contains(ada);

    }

    @Test
    void shouldFindByAgeANDName() {
        Person ada = Person.builder()
                .withAge(20).withName("Ada").build();

        when(template.select(Mockito.any(DocumentQuery.class)))
                .thenReturn(Stream.of(ada));

        Set<Person> persons = personRepository.findByAgeAndName(20, "name");
        ArgumentCaptor<DocumentQuery> captor = ArgumentCaptor.forClass(DocumentQuery.class);
        verify(template).select(captor.capture());
        assertThat(persons).contains(ada);

    }

    @Test
    void shouldFindByNameANDAgeOrderByName() {
        Person ada = Person.builder()
                .withAge(20).withName("Ada").build();

        when(template.select(Mockito.any(DocumentQuery.class)))
                .thenReturn(Stream.of(ada));

        Stream<Person> persons = personRepository.findByNameAndAgeOrderByName("name", 20);
        ArgumentCaptor<DocumentQuery> captor = ArgumentCaptor.forClass(DocumentQuery.class);
        verify(template).select(captor.capture());
        assertThat(persons.collect(Collectors.toList())).contains(ada);

    }

    @Test
    void shouldFindByNameANDAgeOrderByAge() {
        Person ada = Person.builder()
                .withAge(20).withName("Ada").build();

        when(template.select(Mockito.any(DocumentQuery.class)))
                .thenReturn(Stream.of(ada));

        Queue<Person> persons = personRepository.findByNameAndAgeOrderByAge("name", 20);
        ArgumentCaptor<DocumentQuery> captor = ArgumentCaptor.forClass(DocumentQuery.class);
        verify(template).select(captor.capture());
        assertThat(persons).contains(ada);

    }

    @Test
    void shouldDeleteByName() {
        ArgumentCaptor<DocumentDeleteQuery> captor = ArgumentCaptor.forClass(DocumentDeleteQuery.class);
        personRepository.deleteByName("Ada");
        verify(template).delete(captor.capture());
        DocumentDeleteQuery deleteQuery = captor.getValue();
        DocumentCondition condition = deleteQuery.condition().get();
        assertEquals("Person", deleteQuery.name());
        assertEquals(Condition.EQUALS, condition.condition());
        assertEquals(Document.of("name", "Ada"), condition.document());

    }

    @Test
    void shouldFindById() {
        personRepository.findById(10L);
        verify(template).find(Person.class, 10L);
    }

    @Test
    void shouldFindByIds() {
        when(template.find(Mockito.eq(Person.class), Mockito.any(Long.class)))
                .thenReturn(Optional.of(Person.builder().build()));

        personRepository.findAllById(singletonList(10L)).toList();
        verify(template).find(Person.class, 10L);
        personRepository.findAllById(Arrays.asList(10L, 11L, 12L)).toList();
        verify(template, times(4)).find(Mockito.eq(Person.class), any(Long.class));
    }

    @Test
    void shouldDeleteById() {
        personRepository.deleteById(10L);
        verify(template).delete(Person.class, 10L);

    }

    @Test
    void shouldDeleteByIds() {
        personRepository.deleteAllById(singletonList(10L));
        verify(template).delete(Person.class, 10L);

        personRepository.deleteAllById(asList(1L, 2L, 3L));
        verify(template, times(4)).delete(Mockito.eq(Person.class), any(Long.class));
    }


    @Test
    void shouldContainsById() {
        when(template.find(Mockito.eq(Person.class), Mockito.any(Long.class)))
                .thenReturn(Optional.of(Person.builder().build()));

        assertTrue(personRepository.existsById(10L));
        Mockito.verify(template).find(Person.class, 10L);

        when(template.find(Mockito.eq(Person.class), Mockito.any(Long.class)))
                .thenReturn(Optional.empty());
        assertFalse(personRepository.existsById(10L));

    }

    @Test
    void shouldFindAll() {
        Person ada = Person.builder()
                .withAge(20).withName("Ada").build();

        when(template.select(any(DocumentQuery.class)))
                .thenReturn(Stream.of(ada));

        personRepository.findAll().toList();
        ArgumentCaptor<Class<?>> captor = ArgumentCaptor.forClass(Class.class);
        verify(template).findAll(captor.capture());
        assertEquals(captor.getValue(), Person.class);

    }

    @Test
    void shouldDeleteAll() {
        personRepository.deleteAll();
        ArgumentCaptor<Class<?>> captor = ArgumentCaptor.forClass(Class.class);
        verify(template).deleteAll(captor.capture());
        assertEquals(captor.getValue(), Person.class);

    }

    @Test
    void shouldDeleteEntity(){
        Person person = Person.builder().withId(1L).withAge(20).withName("Ada").build();
        personRepository.delete(person);
        verify(template).delete(Person.class, 1L);
    }

    @Test
    void shouldDeleteEntities(){
        Person person = Person.builder().withId(1L).withAge(20).withName("Ada").build();
        personRepository.deleteAll(List.of(person));
        verify(template).delete(Person.class, 1L);
    }


    @Test
    void shouldReturnToString() {
        assertNotNull(personRepository.toString());
    }

    @Test
    void shouldReturnSameHashCode() {
        assertEquals(personRepository.hashCode(), personRepository.hashCode());
    }

    @Test
    void shouldReturnNotNull() {
        assertNotNull(personRepository);
    }

    @Test
    void shouldFindByNameAndAgeGreaterEqualThan() {
        Person ada = Person.builder()
                .withAge(20).withName("Ada").build();

        when(template.select(any(DocumentQuery.class)))
                .thenReturn(Stream.of(ada));

        personRepository.findByNameAndAgeGreaterThanEqual("Ada", 33);
        ArgumentCaptor<DocumentQuery> captor = ArgumentCaptor.forClass(DocumentQuery.class);
        verify(template).select(captor.capture());
        DocumentQuery query = captor.getValue();
        DocumentCondition condition = query.condition().get();
        assertEquals("Person", query.name());
        assertEquals(AND, condition.condition());
        List<DocumentCondition> conditions = condition.document().get(new TypeReference<>() {
        });
        DocumentCondition columnCondition = conditions.get(0);
        DocumentCondition columnCondition2 = conditions.get(1);

        assertEquals(Condition.EQUALS, columnCondition.condition());
        assertEquals("Ada", columnCondition.document().get());
        assertTrue(columnCondition.document().name().contains("name"));

        assertEquals(Condition.GREATER_EQUALS_THAN, columnCondition2.condition());
        assertEquals(33, columnCondition2.document().get());
        assertTrue(columnCondition2.document().name().contains("age"));

    }

    @Test
    void shouldFindByGreaterThan() {
        Person ada = Person.builder()
                .withAge(20).withName("Ada").build();

        when(template.select(any(DocumentQuery.class)))
                .thenReturn(Stream.of(ada));

        personRepository.findByAgeGreaterThan(33);
        ArgumentCaptor<DocumentQuery> captor = ArgumentCaptor.forClass(DocumentQuery.class);
        verify(template).select(captor.capture());
        DocumentQuery query = captor.getValue();
        DocumentCondition condition = query.condition().get();
        assertEquals("Person", query.name());
        assertEquals(GREATER_THAN, condition.condition());
        assertEquals(Document.of("age", 33), condition.document());

    }

    @Test
    void shouldFindByAgeLessThanEqual() {
        Person ada = Person.builder()
                .withAge(20).withName("Ada").build();

        when(template.select(any(DocumentQuery.class)))
                .thenReturn(Stream.of(ada));

        personRepository.findByAgeLessThanEqual(33);
        ArgumentCaptor<DocumentQuery> captor = ArgumentCaptor.forClass(DocumentQuery.class);
        verify(template).select(captor.capture());
        DocumentQuery query = captor.getValue();
        DocumentCondition condition = query.condition().get();
        assertEquals("Person", query.name());
        assertEquals(LESSER_EQUALS_THAN, condition.condition());
        assertEquals(Document.of("age", 33), condition.document());

    }

    @Test
    void shouldFindByAgeLessEqual() {
        Person ada = Person.builder()
                .withAge(20).withName("Ada").build();

        when(template.select(any(DocumentQuery.class)))
                .thenReturn(Stream.of(ada));

        personRepository.findByAgeLessThan(33);
        ArgumentCaptor<DocumentQuery> captor = ArgumentCaptor.forClass(DocumentQuery.class);
        verify(template).select(captor.capture());
        DocumentQuery query = captor.getValue();
        DocumentCondition condition = query.condition().get();
        assertEquals("Person", query.name());
        assertEquals(LESSER_THAN, condition.condition());
        assertEquals(Document.of("age", 33), condition.document());

    }


    @Test
    void shouldFindByAgeBetween() {
        Person ada = Person.builder()
                .withAge(20).withName("Ada").build();

        when(template.select(any(DocumentQuery.class)))
                .thenReturn(Stream.of(ada));

        personRepository.findByAgeBetween(10, 15);
        ArgumentCaptor<DocumentQuery> captor = ArgumentCaptor.forClass(DocumentQuery.class);
        verify(template).select(captor.capture());
        DocumentQuery query = captor.getValue();
        DocumentCondition condition = query.condition().get();
        assertEquals("Person", query.name());
        assertEquals(BETWEEN, condition.condition());
        List<Value> values = condition.document().get(new TypeReference<>() {
        });
        assertEquals(Arrays.asList(10, 15), values.stream().map(Value::get).collect(Collectors.toList()));
        assertTrue(condition.document().name().contains("age"));

    }


    @Test
    void shouldFindByNameLike() {
        Person ada = Person.builder()
                .withAge(20).withName("Ada").build();

        when(template.select(any(DocumentQuery.class)))
                .thenReturn(Stream.of(ada));

        personRepository.findByNameLike("Ada");
        ArgumentCaptor<DocumentQuery> captor = ArgumentCaptor.forClass(DocumentQuery.class);
        verify(template).select(captor.capture());
        DocumentQuery query = captor.getValue();
        DocumentCondition condition = query.condition().get();
        assertEquals("Person", query.name());
        assertEquals(LIKE, condition.condition());
        assertEquals(Document.of("name", "Ada"), condition.document());
    }

    @Test
    void shouldFindByStringWhenFieldIsSet() {
        Vendor vendor = new Vendor("vendor");
        vendor.setPrefixes(Collections.singleton("prefix"));

        when(template.select(any(DocumentQuery.class)))
                .thenReturn(Stream.of(vendor));

        vendorRepository.findByPrefixes("prefix");

        ArgumentCaptor<DocumentQuery> captor = ArgumentCaptor.forClass(DocumentQuery.class);
        verify(template).singleResult(captor.capture());
        DocumentQuery query = captor.getValue();
        DocumentCondition condition = query.condition().get();
        assertEquals("vendors", query.name());
        assertEquals(EQUALS, condition.condition());
        assertEquals(Document.of("prefixes", "prefix"), condition.document());

    }

    @Test
    void shouldFindByIn() {
        Vendor vendor = new Vendor("vendor");
        vendor.setPrefixes(Collections.singleton("prefix"));

        when(template.select(any(DocumentQuery.class)))
                .thenReturn(Stream.of(vendor));

        vendorRepository.findByPrefixesIn(singletonList("prefix"));

        ArgumentCaptor<DocumentQuery> captor = ArgumentCaptor.forClass(DocumentQuery.class);
        verify(template).singleResult(captor.capture());
        DocumentQuery query = captor.getValue();
        DocumentCondition condition = query.condition().get();
        assertEquals("vendors", query.name());
        assertEquals(IN, condition.condition());

    }


    @Test
    void shouldConvertFieldToTheType() {
        Person ada = Person.builder()
                .withAge(20).withName("Ada").build();

        when(template.select(any(DocumentQuery.class)))
                .thenReturn(Stream.of(ada));

        personRepository.findByAge("120");
        ArgumentCaptor<DocumentQuery> captor = ArgumentCaptor.forClass(DocumentQuery.class);
        verify(template).select(captor.capture());
        DocumentQuery query = captor.getValue();
        DocumentCondition condition = query.condition().get();
        assertEquals("Person", query.name());
        assertEquals(EQUALS, condition.condition());
        assertEquals(Document.of("age", 120), condition.document());
    }

    @Test
    void shouldFindByActiveTrue() {
        Person ada = Person.builder()
                .withAge(20).withName("Ada").build();

        when(template.select(any(DocumentQuery.class)))
                .thenReturn(Stream.of(ada));

        personRepository.findByActiveTrue();
        ArgumentCaptor<DocumentQuery> captor = ArgumentCaptor.forClass(DocumentQuery.class);
        verify(template).select(captor.capture());
        DocumentQuery query = captor.getValue();
        DocumentCondition condition = query.condition().get();
        assertEquals("Person", query.name());
        assertEquals(EQUALS, condition.condition());
        assertEquals(Document.of("active", true),
                condition.document());
    }

    @Test
    void shouldFindByActiveFalse() {
        Person ada = Person.builder()
                .withAge(20).withName("Ada").build();

        when(template.select(any(DocumentQuery.class)))
                .thenReturn(Stream.of(ada));

        personRepository.findByActiveFalse();
        ArgumentCaptor<DocumentQuery> captor = ArgumentCaptor.forClass(DocumentQuery.class);
        verify(template).select(captor.capture());
        DocumentQuery query = captor.getValue();
        DocumentCondition condition = query.condition().get();
        assertEquals("Person", query.name());
        assertEquals(EQUALS, condition.condition());
        assertEquals(Document.of("active", false), condition.document());
    }

    @Test
    void shouldExecuteJNoSQLQuery() {
        personRepository.findByQuery();
        verify(template).query("select * from Person");
    }

    @Test
    void shouldExecuteJNoSQLPrepare() {
        PreparedStatement statement = Mockito.mock(PreparedStatement.class);
        when(template.prepare(Mockito.anyString())).thenReturn(statement);
        personRepository.findByQuery("Ada");
        verify(statement).bind("id", "Ada");
    }

    @Test
    void shouldFindBySalary_Currency() {
        Person ada = Person.builder()
                .withAge(20).withName("Ada").build();

        when(template.select(any(DocumentQuery.class)))
                .thenReturn(Stream.of(ada));

        personRepository.findBySalary_Currency("USD");
        ArgumentCaptor<DocumentQuery> captor = ArgumentCaptor.forClass(DocumentQuery.class);
        verify(template).select(captor.capture());
        DocumentQuery query = captor.getValue();
        DocumentCondition condition = query.condition().get();
        final Document document = condition.document();
        assertEquals("Person", query.name());
        assertEquals("salary.currency", document.name());

    }

    @Test
    void shouldFindBySalary_CurrencyAndSalary_Value() {
        Person ada = Person.builder()
                .withAge(20).withName("Ada").build();
        when(template.select(any(DocumentQuery.class)))
                .thenReturn(Stream.of(ada));
        personRepository.findBySalary_CurrencyAndSalary_Value("USD", BigDecimal.TEN);
        ArgumentCaptor<DocumentQuery> captor = ArgumentCaptor.forClass(DocumentQuery.class);
        verify(template).select(captor.capture());
        DocumentQuery query = captor.getValue();
        DocumentCondition condition = query.condition().get();
        final Document document = condition.document();
        final List<DocumentCondition> conditions = document.get(new TypeReference<>() {
        });
        final List<String> names = conditions.stream().map(DocumentCondition::document)
                .map(Document::name).collect(Collectors.toList());
        assertEquals("Person", query.name());
        assertThat(names).contains("salary.currency", "salary.value");

    }

    @Test
    void shouldFindBySalary_CurrencyOrderByCurrency_Name() {
        Person ada = Person.builder()
                .withAge(20).withName("Ada").build();

        when(template.select(any(DocumentQuery.class)))
                .thenReturn(Stream.of(ada));

        personRepository.findBySalary_CurrencyOrderByCurrency_Name("USD");
        ArgumentCaptor<DocumentQuery> captor = ArgumentCaptor.forClass(DocumentQuery.class);
        verify(template).select(captor.capture());
        DocumentQuery query = captor.getValue();
        DocumentCondition condition = query.condition().get();
        final Sort sort = query.sorts().get(0);
        final Document document = condition.document();
        assertEquals("Person", query.name());
        assertEquals("salary.currency", document.name());
        assertEquals("currency.name", sort.property());

    }

    @Test
    void shouldCountByName() {
        when(template.count(any(DocumentQuery.class)))
                .thenReturn(10L);

        var result = personRepository.countByName("Poliana");
        Assertions.assertEquals(10L, result);
        ArgumentCaptor<DocumentQuery> captor = ArgumentCaptor.forClass(DocumentQuery.class);
        verify(template).count(captor.capture());
        DocumentQuery query = captor.getValue();
        DocumentCondition condition = query.condition().get();
        final Document document = condition.document();
        assertEquals("Person", query.name());
        assertEquals("name", document.name());
        assertEquals("Poliana", document.get());
    }

    @Test
    void shouldExistsByName() {
        when(template.exists(any(DocumentQuery.class)))
                .thenReturn(true);

        var result = personRepository.existsByName("Poliana");
        assertTrue(result);
        ArgumentCaptor<DocumentQuery> captor = ArgumentCaptor.forClass(DocumentQuery.class);
        verify(template).exists(captor.capture());
        DocumentQuery query = captor.getValue();
        DocumentCondition condition = query.condition().get();
        final Document document = condition.document();
        assertEquals("Person", query.name());
        assertEquals("name", document.name());
        assertEquals("Poliana", document.get());
    }

    @Test
    void shouldExecuteCustomRepository(){
        PersonStatisticRepository.PersonStatistic statistics = personRepository.statistics("Salvador");
        assertThat(statistics).isNotNull();
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(statistics.average()).isEqualTo(26);
            softly.assertThat(statistics.sum()).isEqualTo(26);
            softly.assertThat(statistics.max()).isEqualTo(26);
            softly.assertThat(statistics.min()).isEqualTo(26);
            softly.assertThat(statistics.count()).isEqualTo(1);
            softly.assertThat(statistics.city()).isEqualTo("Salvador");
        });
    }

    interface PersonRepository extends PageableRepository<Person, Long>, PersonStatisticRepository {


        long countByName(String name);

        boolean existsByName(String name);

        List<Person> findBySalary_Currency(String currency);

        List<Person> findBySalary_CurrencyAndSalary_Value(String currency, BigDecimal value);

        List<Person> findBySalary_CurrencyOrderByCurrency_Name(String currency);

        Person findByName(String name);

        List<Person> findByAge(String age);

        void deleteByName(String name);

        List<Person> findByNameAndAge(String name, Integer age);

        Set<Person> findByAgeAndName(Integer age, String name);

        Stream<Person> findByNameAndAgeOrderByName(String name, Integer age);

        Queue<Person> findByNameAndAgeOrderByAge(String name, Integer age);

        Set<Person> findByNameAndAgeGreaterThanEqual(String name, Integer age);

        Set<Person> findByAgeGreaterThan(Integer age);

        Set<Person> findByAgeLessThanEqual(Integer age);

        Set<Person> findByAgeLessThan(Integer age);

        Set<Person> findByAgeBetween(Integer ageA, Integer ageB);

        Set<Person> findByNameLike(String name);

        @Query("select * from Person")
        Optional<Person> findByQuery();

        @Query("select * from Person where id = @id")
        Optional<Person> findByQuery(@Param("id") String id);

        List<Person> findByActiveFalse();

        List<Person> findByActiveTrue();
    }

    public interface VendorRepository extends PageableRepository<Vendor, String> {

        Vendor findByPrefixes(String prefix);

        Vendor findByPrefixesIn(List<String> prefix);

    }
}
