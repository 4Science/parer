package it.ivscience.parer.content.dao;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.List;

/**
 * Abstract implementation of the DSpace DAO pattern (Option A: Native SessionFactory).
 *
 * Uses {@link SessionFactory#getCurrentSession()} — requires an active
 * transaction (managed by the Service layer via @Transactional).
 * Does not use JPA EntityManager.
 *
 * @param <T> entity type
 */
public abstract class AbstractHibernateDAO<T> implements GenericDAO<T> {

    @Autowired
    @Qualifier("sessionFactory")
    private SessionFactory sessionFactory;

    protected Session getSession() {
        return sessionFactory.getCurrentSession();
    }

    @Override
    public T create(T entity) {
        getSession().persist(entity);
        return entity;
    }

    @Override
    public T save(T entity) {
        return getSession().merge(entity);
    }

    @Override
    public void delete(T entity) {
        Session s = getSession();
        s.remove(s.contains(entity) ? entity : s.merge(entity));
    }

    @Override
    public T findByID(Class<T> clazz, Integer id) {
        return getSession().get(clazz, id);
    }

    @Override
    public List<T> findAll(Class<T> clazz) {
        return getSession()
                .createQuery("FROM " + clazz.getSimpleName(), clazz)
                .getResultList();
    }
}