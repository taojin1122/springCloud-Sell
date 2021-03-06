package com.reder.order.service.impl;


import com.reder.order.dataobject.OrderDetail;
import com.reder.order.dataobject.OrderMaster;

import com.reder.order.dto.OrderDTO;
import com.reder.order.enums.OrderStatusEnum;
import com.reder.order.enums.PayStatusEnum;
import com.reder.order.enums.ResultEnum;
import com.reder.order.exception.OrderException;
import com.reder.order.repository.OrderDetailRepository;
import com.reder.order.repository.OrderMasterRepository;
import com.reder.order.service.OrderService;
import com.reder.product.client.ProductClient;
import com.reder.product.common.DecreaseStockInput;
import com.reder.product.common.ProductInfoOutput;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderDetailRepository orderDetailRepository;
    @Autowired
    private OrderMasterRepository orderMasterRepository;
    @Autowired
    private ProductClient productClient;



    @Override
    public OrderDTO create(OrderDTO orderDTO) {
        String orderId = String.valueOf(UUID.randomUUID()).replace("-","");
        //1、获取商品信息

        List<String> productIdList = orderDTO.getOrderDetailList().stream()
                                        .map(OrderDetail::getProductId)
                                        .collect(Collectors.toList());

        List<ProductInfoOutput> productInfoList = productClient.listForOrder(productIdList);

        //读redis
        // 减库存并将新值重新设置进redis
        // 订单入库异常，手动回滚redis ---（对数据库代码进行try-catch，如果异常再将redis重新进行设置）


        //2、计算总价
        BigDecimal orderAmount = new BigDecimal(0);
        for (OrderDetail orderDetail : orderDTO.getOrderDetailList()) {
            for (ProductInfoOutput productInfo : productInfoList) {
                if (productInfo.getProductId().equals(orderDetail.getProductId())) {
                    //单价*数量
                    orderAmount = productInfo.getProductPrice()
                                .multiply(new BigDecimal(orderDetail.getProductQuantity()))
                                .add(orderAmount);
                    BeanUtils.copyProperties(productInfo,orderDetail);
                    orderDetail.setOrderId(orderId);
                    orderDetail.setDetailId(String.valueOf(UUID.randomUUID()).replace("-",""));
                  //订单详情入库
                    orderDetailRepository.save(orderDetail);
                }
            }
        }
        // 扣库存
           List<DecreaseStockInput> cartDTOList = orderDTO.getOrderDetailList().stream()
                                            .map(e -> new DecreaseStockInput(e.getProductId(),e.getProductQuantity()))
                                            .collect(Collectors.toList());
        productClient.decreaseStock(cartDTOList);
        //订单入库
        OrderMaster orderMaster = new OrderMaster();
        orderDTO.setOrderId(orderId);
        BeanUtils.copyProperties(orderDTO,orderMaster);
        orderMaster.setOrderAmount(orderAmount);
        orderMaster.setOrderStatus(OrderStatusEnum.NEW.getCode());
        orderMaster.setPayStatus(PayStatusEnum.WAIT.getCode());
        orderMasterRepository.save(orderMaster);
        return orderDTO;
    }

    @Override
    public OrderDTO finish(String orderId) {
        // 1、 先查询订单
           Optional<OrderMaster>  orderMasterOptional = orderMasterRepository.findById(orderId);
           if (!orderMasterOptional.isPresent()) {
               throw new OrderException(ResultEnum.ORDER_NOT_EXIST);
           }
           // 2、 判断订单状态
            OrderMaster orderMaster = orderMasterOptional.get();
           if (!OrderStatusEnum.NEW.getCode().equals(orderMaster.getOrderStatus())) {
               throw new OrderException(ResultEnum.ORDER_STATUS_ERROR);
           }
        // 3、 修改订单状态为完结
          orderMaster.setOrderStatus(OrderStatusEnum.FINISHED.getCode());
           orderMasterRepository.save(orderMaster);

           // 查询订单详情
           List<OrderDetail> orderDetailList = orderDetailRepository.findByOrderId(orderId);
           if (CollectionUtils.isEmpty(orderDetailList)) {
               throw new OrderException(ResultEnum.ORDER_DETAIL_NOT_EXIST);
           }
           OrderDTO orderDTO = new OrderDTO();
           BeanUtils.copyProperties(orderMaster, orderDTO);
           orderDTO.setOrderDetailList(orderDetailList);
        return orderDTO;
    }
}
