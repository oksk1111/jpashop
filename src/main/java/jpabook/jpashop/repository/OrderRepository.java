package jpabook.jpashop.repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jpabook.jpashop.domain.Order;
import jpabook.jpashop.domain.OrderStatus;
import jpabook.jpashop.domain.QMember;
import jpabook.jpashop.domain.QOrder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.awt.*;
import java.util.List;

import static jpabook.jpashop.domain.QMember.member;
import static jpabook.jpashop.domain.QOrder.order;

@Repository
@RequiredArgsConstructor
public class OrderRepository {

    private final EntityManager em;

    public void save(Order order) {
        em.persist(order);
    }

    public Order findOne(Long id) {
        return em.find(Order.class, id);
    }

    // 복잡하다
    public List<Order> findAllByString(OrderSearch orderSearch) {
        //language=JPAQL
        String jpql = "select o From Order o join o.member m";
        boolean isFirstCondition = true;
        //주문 상태 검색
        if (orderSearch.getOrderStatus() != null) {
            if (isFirstCondition) {
                jpql += " where";
                isFirstCondition = false;
            } else {
                jpql += " and";
            }
            jpql += " o.status = :status";
        }
        //회원 이름 검색
        if (StringUtils.hasText(orderSearch.getMemberName())) {
            if (isFirstCondition) {
                jpql += " where";
                isFirstCondition = false;
            } else {
                jpql += " and";
            }
            jpql += " m.name like :name";
        }
        TypedQuery<Order> query = em.createQuery(jpql, Order.class)
                .setMaxResults(1000); //최대 1000건
        if (orderSearch.getOrderStatus() != null) {
            query = query.setParameter("status", orderSearch.getOrderStatus());
        }
        if (StringUtils.hasText(orderSearch.getMemberName())) {
            query = query.setParameter("name", orderSearch.getMemberName());
        }
        return query.getResultList();
    }

    /**
     * JPA Criteria
     * 안 쓴다 -> queryDSL 쓰자 <- 이거 안알려줬다!!
     */
    public List<Order> findAllByCriteria(OrderSearch orderSerch) {
        return null;
    }

    //QuieryDSL 예제
    public List<Order> findAll(OrderSearch orderSearch) {
        JPAQueryFactory query = new JPAQueryFactory(em);
        QOrder order = QOrder.order;
        QMember member = QMember.member;

        return query
                .select(order)
                .from(order)
                .join(order.member, member)
                .where(statusEq(orderSearch.getOrderStatus()),
                        nameLike(orderSearch.getMemberName()))
                .limit(1000)
                .fetch();
    }
    private BooleanExpression statusEq(OrderStatus statusCond) {
        if (statusCond == null) {
            return null;
        }
        return order.status.eq(statusCond);
    }
    private BooleanExpression nameLike(String nameCond) {
        if (!StringUtils.hasText(nameCond)) {
            return null;
        }
        return member.name.like(nameCond);
    }



    public List<Order> findAllWithMemberDelivery() {
        //fetch join. LAZY와 무관하게 다 가져오기
        //fetch join 하면 모든걸 join 한 상태로 데이터 가져온다. 즉, sql 호출 한번
        return em.createQuery(
                "select o from Order o" +
                        " join fetch o.member m" +
                        " join fetch o.delivery d", Order.class
        ).getResultList();
    }



    //아래는 쓰지 말자
    //굳이 쓰려면 Collection fetch join은 단 1회만 연결해서 수행한다
    public List<Order> findAllWithItem() {
        //쉽게 하려면 쿼리dsl 을 해라...
        //distinct는 DB 데이터가 완전 일치할때만 중복 제거한다
        //하지만 JPA 에서는 한번 더 처리를 해서 일부 중복이 제거된 채로 내려간다. (아래에선 Order 중복을 제거함)
        //하지만 DB에서는 paging 적용이 안된다! (JPA 에서 앱으로 처리할때만 됨)
        //뭘하든 데이터 다 가져오므로 메모리에 차이가 없다;
        return em.createQuery(
                "select distinct o from Order o" +
                        " join fetch o.member m" +
                        " join fetch o.delivery d" +
                        " join fetch o.orderItems oi" +
                        " join fetch oi.item i", Order.class)
//                .setFirstResult(1)
//                .setMaxResults(100)
                . getResultList();
    }

    //ToOne 관계는 페이징이 잘된다.
    //findAllWithItem의 전체 fetch join 한거에 비해 테이블 조인이 아니기에 sql 횟수는 늘지만 join 전에 조회하기에 데이터 중복이 줄어든다.
    public List<Order> findAllWithMemberDelivery(int offset, int limit) {
        //fetch join. LAZY와 무관하게 다 가져오기
        //fetch join 하면 모든걸 join 한 상태로 데이터 가져온다. 즉, sql 호출 한번
        return em.createQuery(
                        "select o from Order o" +
                                " join fetch o.member m" +
                                " join fetch o.delivery d", Order.class)
//                "select o from Order o", Order.class)   //ToOne 관계는 batch_fetch를 적용하면 fetch join 명령어 없어도 최적화가 잘되는 편이다.
                .setFirstResult(offset)
                .setMaxResults(limit)
                .getResultList();
    }
}
