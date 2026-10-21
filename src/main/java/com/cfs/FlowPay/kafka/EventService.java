package com.cfs.FlowPay.kafka;

import com.cfs.FlowPay.Common.Ids;
import org.springframework.stereotype.Service;
import com.cfs.FlowPay.Webhook.WebHookService;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

@Service
public class EventService {

    private final EventRepository repo;
    private final ObjectMapper json;
    private final WebHookService webhooks;

    public EventService(EventRepository repo,ObjectMapper json,WebHookService webhooks){
        this.repo=repo;
        this.json=json;
        this.webhooks=webhooks;
    }

    public void create(String merchantId,String type,String referenceId,long amount){
        Event e = new Event();
        e.id = Ids.next("evt");
        e.merchantId=merchantId;
        e.type=type;
        try{
            e.payload=json.writeValueAsString(
                    Map.of("id",e.id,
                            "type",type,
                            "merchantId",merchantId,
                            "referenceId",referenceId,
                            "amount",amount,
                            "currency",
                            "INR",
                            "createdAt",
                            e.createAt.toString()));
        } catch (Exception ex) {
            throw new IllegalArgumentException("Failed to serialize event payload",ex);
        }
        repo.save(e);
        webhooks.enqueue(e);
    }
}
