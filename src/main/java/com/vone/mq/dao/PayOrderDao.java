package com.vone.mq.dao;

import com.vone.mq.entity.PayOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface PayOrderDao extends JpaRepository<PayOrder, Long>, JpaSpecificationExecutor<PayOrder> {

    PayOrder findByPayId(String payId);
    PayOrder findByOrderId(String orderId);

    @Transactional
    @Modifying
    @Query(value = "update pay_order set state=?1 where id=?2", nativeQuery = true)
    int setState(int state,long id);

    @Transactional
    @Modifying
    @Query(value = """
            update pay_order
            set state=2,
                pay_date=case when pay_date=0 then ?2 else pay_date end,
                close_date=case when close_date=0 then ?2 else close_date end
            where id=?1
              and state in (-1, 0)
            """, nativeQuery = true)
    int markManualCallbackPending(long id, long confirmedAt);

    List<PayOrder> findAllByStateAndCreateDateLessThan(int state, long createDate);

    PayOrder findByReallyPriceAndStateAndType(double reallyPrice,int state,int type);

    PayOrder findByPayDate(Long payDate);

    @Transactional
    int deleteByState(int state);


    @Transactional
    @Modifying
    @Query(value = "delete from pay_order where create_date<?1", nativeQuery = true)
    int deleteByAfterCreateDate(String date);

}
