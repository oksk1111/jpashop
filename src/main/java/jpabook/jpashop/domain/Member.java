package jpabook.jpashop.domain;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import javax.validation.constraints.NotEmpty;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter @Setter
public class Member {

    @Id @GeneratedValue
    @Column(name = "member_id")
    private Long id;

    @NotEmpty   //화면 종속적인 validation은 API 용으로는 적합하지 않을 수 있다
    private String name;

    @Embedded
    private Address address;

    //@JsonIgnore     //노출하고 싶지 않을때
    @OneToMany(mappedBy = "member")
    private List<Order> orders = new ArrayList<>();
}
