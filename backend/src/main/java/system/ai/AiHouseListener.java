package system.ai;

import org.slf4j.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;

import system.house.HouseModels;

@Component
public class AiHouseListener {
    private static final Logger log = LoggerFactory.getLogger(AiHouseListener.class);
    private final AiService service;

    public AiHouseListener(AiService service) {
        this.service = service;
    }

    @Async("aiExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void changed(HouseModels.Changed event) {
        try {
            service.analyzeInternal(event.houseId(), event.actorId());
        } catch (Exception ex) {
            log.error("文案分析任务失败 house={}", event.houseId(), ex);
        }
    }
}
