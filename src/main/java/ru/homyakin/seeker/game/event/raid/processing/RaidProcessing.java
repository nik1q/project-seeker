package ru.homyakin.seeker.game.event.raid.processing;

import java.util.ArrayList;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.homyakin.seeker.game.battle.two_team.TwoPersonageTeamsBattle;
import ru.homyakin.seeker.game.battle.two_team.TwoTeamBattleWinner;
import ru.homyakin.seeker.game.event.models.EventResult;
import ru.homyakin.seeker.game.event.launched.LaunchedEvent;
import ru.homyakin.seeker.game.event.raid.RaidService;
import ru.homyakin.seeker.game.event.raid.generator.RaidGenerator;
import ru.homyakin.seeker.game.event.raid.models.GeneratedItemResult;
import ru.homyakin.seeker.game.event.launched.LaunchedEventService;
import ru.homyakin.seeker.game.event.raid.models.LaunchedRaidEvent;
import ru.homyakin.seeker.game.models.Money;
import ru.homyakin.seeker.game.personage.PersonageService;
import ru.homyakin.seeker.game.personage.event.PersonageEventService;
import ru.homyakin.seeker.game.personage.event.RaidParticipant;
import ru.homyakin.seeker.game.personage.models.Personage;
import ru.homyakin.seeker.game.personage.models.PersonageRaidResult;
import ru.homyakin.seeker.game.event.service.GroupEventService;
import ru.homyakin.seeker.game.stats.action.GroupStatsService;

@Service
public class RaidProcessing {
    private static final Logger logger = LoggerFactory.getLogger(RaidProcessing.class);
    private final PersonageService personageService;
    private final TwoPersonageTeamsBattle twoPersonageTeamsBattle;
    private final RaidService raidService;
    private final RaidItemGenerator raidItemGenerator;
    private final RaidGenerator raidGenerator;
    private final RaidRewardGenerator raidRewardGenerator;
    private final LaunchedEventService launchedEventService;
    private final PersonageEventService personageEventService;
    private final GroupEventService groupEventService;
    private final GroupStatsService groupStatsService;

    public RaidProcessing(
        PersonageService personageService,
        TwoPersonageTeamsBattle twoPersonageTeamsBattle,
        RaidService raidService,
        RaidItemGenerator raidItemGenerator,
        RaidGenerator raidGenerator,
        RaidRewardGenerator raidRewardGenerator,
        LaunchedEventService launchedEventService,
        PersonageEventService personageEventService,
        GroupEventService groupEventService,
        GroupStatsService groupStatsService
    ) {
        this.personageService = personageService;
        this.twoPersonageTeamsBattle = twoPersonageTeamsBattle;
        this.raidService = raidService;
        this.raidItemGenerator = raidItemGenerator;
        this.raidGenerator = raidGenerator;
        this.raidRewardGenerator = raidRewardGenerator;
        this.launchedEventService = launchedEventService;
        this.personageEventService = personageEventService;
        this.groupEventService = groupEventService;
        this.groupStatsService = groupStatsService;
    }

    public EventResult.RaidResult process(LaunchedEvent launchedEvent) {
        final var raid = raidService.getByEventId(launchedEvent.eventId())
            .orElseThrow(() -> new IllegalStateException("Raid must be present"));
        final var raidEvent = LaunchedRaidEvent.fromLaunchedEvent(launchedEvent);

        final var participants = personageEventService.getRaidParticipants(launchedEvent.id());
        if (participants.isEmpty()) {
            logger.info("Raid {} is expired", launchedEvent.id());
            final var result = EventResult.RaidResult.Expired.INSTANCE;
            launchedEventService.updateResult(raidEvent, result);
            return result;
        }

        final var idToParticipant = participants.stream().collect(Collectors.toMap(it -> it.personage().id(), it -> it));
        final var personages = participants.stream().map(RaidParticipant::personage).map(Personage::toBattlePersonage).toList();
        final var result = twoPersonageTeamsBattle.battle(
            raidGenerator.generate(raid, raidEvent, personages),
            personages
        );
        boolean doesParticipantsWin = result.winner() == TwoTeamBattleWinner.SECOND_TEAM;

        final var generatedItems = new ArrayList<GeneratedItemResult>();

        final var raidResults = result.secondTeamResults().personageResults().stream()
            .map(battleResult -> {
                final var participant = idToParticipant.get(battleResult.personage().id());
                final var reward = new Money(
                    raidRewardGenerator.calculateReward(
                        doesParticipantsWin,
                        battleResult,
                        participant.params().isExhausted()
                    )
                );
                personageService.addMoney(
                    participant.personage(),
                    reward
                );
                final var generatedItem = raidItemGenerator.generateItem(
                    doesParticipantsWin,
                    battleResult.personage(),
                    participant.params().isExhausted()
                );
                generatedItem.ifPresent(generatedItems::add);
                return new PersonageRaidResult(
                    participant,
                    battleResult.stats(),
                    reward,
                    generatedItem.map(GeneratedItemResult::item)
                );
            })
            .toList();

        final var raidResult = new EventResult.RaidResult.Completed(
            doesParticipantsWin
                ? EventResult.RaidResult.Completed.Status.SUCCESS
                : EventResult.RaidResult.Completed.Status.FAILURE,
            raid,
            result.firstTeamResults().personageResults(),
            raidResults,
            generatedItems,
            calculateRaidPoints(raidEvent.raidParams().raidLevel(), doesParticipantsWin)
        );
        personageService.saveRaidResults(raidResult.personageResults(), launchedEvent);
        groupEventService.updateRaidLevel(launchedEvent.id(), doesParticipantsWin);
        launchedEventService.updateResult(raidEvent, raidResult);
        logger.info("Raid {} status is {}", launchedEvent.id(), raidResult.status());
        return raidResult;
    }

    private int calculateRaidPoints(int raidLevel, boolean isWin) {
        if (isWin) {
            return 2 * raidLevel;
        } else {
            return raidLevel;
        }
    }
}
