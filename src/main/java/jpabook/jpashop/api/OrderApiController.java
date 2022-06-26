package jpabook.jpashop.api;

import jpabook.jpashop.domain.Address;
import jpabook.jpashop.domain.Order;
import jpabook.jpashop.domain.OrderItem;
import jpabook.jpashop.domain.OrderStatus;
import jpabook.jpashop.repository.OrderRepository;
import jpabook.jpashop.repository.OrderSearch;
import jpabook.jpashop.repository.order.query.OrderFlatDto;
import jpabook.jpashop.repository.order.query.OrderQueryDto;
import jpabook.jpashop.repository.order.query.OrderQueryRepository;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.jaxb.SpringDataJaxb;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;


//조회구현 접근 방법순서
//v1 -> v3.1 (엔티티 조회 - 패치조인, batchfetch 가능, 성능최적화 가능)
//엔티티조회 안되면 DTO 조회 (코드가 길어짐) - X
//DTO 조회로 안되면 NativeSQL or 스프링 JdbcTemplate
//* 주: 엔티티는 캐시하면 안됨(영속성 컨텍스트관련). DTO는 캐시가능.
@RestController
@RequiredArgsConstructor
public class OrderApiController {

    private final OrderRepository orderRepository;
    private final OrderQueryRepository orderQueryRepository;

    //API로 Entity를 직접 노출하는건 안 좋다. 유지보수문제. 코드가 바뀌면 스펙이 바뀐다.
    //사용X
    @GetMapping("/api/v1/orders")
    public List<Order> ordersV1() {
        List<Order> all = orderRepository.findAllByString(new OrderSearch());
        for (Order order : all) {
            order.getMember().getName();
            order.getDelivery().getAddress();
            
            List<OrderItem> orderItems = order.getOrderItems();
            orderItems.stream().forEach(o -> o.getItem().getName());    //조회하면서 초기화
        }
        return all;
    }

    //Entity 대신 DTO로. 다만 아래것은 성능이슈가 있다. sql 횟수가 많다.
    @GetMapping("/api/v2/orders")
    public List<OrderDto> ordersV2() {
        List<Order> orders = orderRepository.findAllByString(new OrderSearch());
        List<OrderDto> collect = orders.stream()
                .map(o -> new OrderDto(o))
                .collect(Collectors.toList());
        return collect;
    }

    //v2와 다른건 코드는 동일하나 jpql 만 다르다. 성능은 sql 단한번!!
    //페이징이 안된다. (DB 내부에서)
    @GetMapping("/api/v3/orders")
    public List<OrderDto> ordersV3() {
        List<Order> orders = orderRepository.findAllWithItem();
        List<OrderDto> result = orders.stream()
                .map(o -> new OrderDto(o))
                .collect(Collectors.toList());
        return result;
    }

    //대부분 이걸 쓰자
    //컬렉션은 패치 조인시 페이징 불가능
    //ToOne 관계는 페치 조인으로 쿼리 수 최적화
    //컬렉션은 페치 조인 대신에 지연 로딩 유지 + hibernate.defailt_batch_fetch_size + @BatchSize로 최적화
    @GetMapping("/api/v3.1/orders")
    public List<OrderDto> ordersV3_page(
            @RequestParam(value = "offset", defaultValue = "0") int offset,
            @RequestParam(value = "limit", defaultValue = "100") int limit
    ) {
        //ToOne 관계는 fetch join 해서 한번의 sql 쿼리로 가져와라!
        List<Order> orders = orderRepository.findAllWithMemberDelivery();
        List<OrderDto> result = orders.stream()
                .map(o -> new OrderDto(o))
                .collect(Collectors.toList());
        return result;
    }

    //JPA에서 DTO를 직접 조회
    @GetMapping("/api/v4/orders")
    public List<OrderQueryDto> ordersV4() {
        return orderQueryRepository.findOrderQueryDtos();
    }

    //컬렉션 조회 최적화
    @GetMapping("/api/v5/orders")
    public List<OrderQueryDto> ordersV5() {
        return orderQueryRepository.findAllByDto_optimization();
    }

    //플랙 데이터 최적화 - 개발자가 직접 데이터 형태 만들기
    @GetMapping("/api/v6/orders")
    public List<OrderFlatDto> ordersV6() {  //-> OrderQueryDto
        //미구현
        return orderQueryRepository.findAllByDto_flat();
    }

    @Data
    static class OrderDto {

        private Long orderId;
        private String name;
        private LocalDateTime orderDate;
        private OrderStatus orderStatus;
        private Address address;
        //주: DTO로 반환하기로 했으면 Entity를 Wrapping 해서도 안된다!
        private List<OrderItemDto> orderItems;

        public OrderDto(Order order) {
            orderId = order.getId();
            name = order.getMember().getName();
            orderDate = order.getOrderDate();
            orderStatus = order.getStatus();
            address = order.getDelivery().getAddress();
            //order.getOrderItems().stream().forEach(o -> o.getItem().getName());
            //orderItems = order.getOrderItems();
            orderItems = order.getOrderItems().stream()
                    .map(orderItem -> new OrderItemDto(orderItem))
                    .collect(Collectors.toList());
        }
    }

    //DTO에 Getter 기능만 사용하면 @Data 말고 아래꺼로 한정하는게 낫다
    //아래 DTO는 조회용이기에 @Getter만 해도 된다
    @Getter
    static class OrderItemDto {

        private String itemName;    //상품명
        private int orderPrice; //주문가격
        private int count;  //주문수량

        public OrderItemDto(OrderItem orderItem) {
            itemName = orderItem.getItem().getName();
            orderPrice = orderItem.getItem().getPrice();
            count = orderItem.getCount();
        }
    }
}
