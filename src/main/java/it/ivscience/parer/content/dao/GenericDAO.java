package it.ivscience.parer.content.dao;

import java.util.List;

/**
 * Generic DAO interface, inspired by the DSpace pattern.
 * Exposes basic CRUD operations on a single entity.
 *
 * @param <T> entity type
 */
public interface GenericDAO<T> {

    T create(T t);

    T save(T t);

    void delete(T t);

    T findByID(Class<T> clazz, Integer id);

    List<T> findAll(Class<T> clazz);
}