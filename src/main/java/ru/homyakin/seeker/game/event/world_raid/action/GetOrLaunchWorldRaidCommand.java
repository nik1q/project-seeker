package ru.homyakin.seeker.game.event.world_raid.action;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.homyakin.seeker.game.event.world_raid.entity.ActiveWorldRaid;
import ru.homyakin.seeker.game.event.world_raid.entity.ResearchGenerator;
import ru.homyakin.seeker.game.event.world_raid.entity.WorldRaidConfig;
import ru.homyakin.seeker.game.event.world_raid.entity.WorldRaidStorage;
import ru.homyakin.seeker.game.models.Money;
import ru.homyakin.seeker.utils.RandomUtils;

@Component
public class GetOrLaunchWorldRaidCommand {
    private static final Logger logger = LoggerFactory.getLogger(GetOrLaunchWorldRaidCommand.class);
    private final WorldRaidStorage storage;
    private final ResearchGenerator researchGenerator;
    private final WorldRaidConfig config;

    public GetOrLaunchWorldRaidCommand(
        WorldRaidStorage storage,
        ResearchGenerator researchGenerator,
        WorldRaidConfig config
    ) {
        this.storage = storage;
        this.researchGenerator = researchGenerator;
        this.config = config;
    }

    /**
     * @return Если рейд существовал, то возвращает его, иначе создаёт новый
     */
    public ActiveWorldRaid execute() {
        final var currentActive = storage.getActive();
        if (currentActive.isPresent()) {
            return currentActive.get();
        }
        final var template = storage.getRandom()
            .orElseThrow(() -> new IllegalStateException("World raid template must be present"));
        logger.info("Launching world raid {}", template.code());
        storage.saveActive(
            template,
            Money.from(RandomUtils.getInPercentRange(config.initFund().value(), 10)),
            researchGenerator.generate()
        );
        return storage.getActive().orElseThrow();
    }
}
