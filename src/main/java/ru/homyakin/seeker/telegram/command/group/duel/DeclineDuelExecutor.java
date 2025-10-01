package ru.homyakin.seeker.telegram.command.group.duel;

import io.vavr.control.Either;
import org.springframework.stereotype.Component;
import ru.homyakin.seeker.game.duel.DuelService;
import ru.homyakin.seeker.game.duel.models.ProcessDuelError;
import ru.homyakin.seeker.game.personage.PersonageService;
import ru.homyakin.seeker.locale.duel.DuelLocalization;
import ru.homyakin.seeker.telegram.TelegramSender;
import ru.homyakin.seeker.telegram.group.GroupUserService;
import ru.homyakin.seeker.telegram.group.models.GroupTg;
import ru.homyakin.seeker.telegram.models.TgPersonageMention;
import ru.homyakin.seeker.telegram.user.UserService;
import ru.homyakin.seeker.telegram.user.models.User;
import ru.homyakin.seeker.telegram.utils.EditMessageTextBuilder;
import ru.homyakin.seeker.utils.models.Success;

@Component
public class DeclineDuelExecutor extends ProcessDuelExecutor<DeclineDuel> {
    private final DuelService duelService;
    private final PersonageService personageService;
    private final UserService userService;

    public DeclineDuelExecutor(
            GroupUserService groupUserService,
            DuelService duelService,
            TelegramSender telegramSender,
            PersonageService personageService,
            UserService userService
    ) {
        super(telegramSender, groupUserService);
        this.duelService = duelService;
        this.personageService = personageService;
        this.userService = userService;
    }

    @Override
    protected Either<ProcessDuelError, Success> processDuel(DeclineDuel command, GroupTg group, User user) {
        final var duel = duelService.getByIdForce(command.duelId());
        final var clickerId = user.personageId();

        if (clickerId.equals(duel.initiatingPersonageId())) {
            return duelService.cancelDuel(duel, clickerId)
                    .peek(success -> {
                        final var acceptorPersonage = personageService.getByIdForce(duel.acceptingPersonageId());
                        final var acceptorUser = userService.getByPersonageIdForce(duel.acceptingPersonageId());
                        telegramSender.send(EditMessageTextBuilder.builder()
                                .chatId(group.id())
                                .messageId(command.messageId())
                                .text(DuelLocalization.duelCanceledByInitiator(group.language(),
                                        TgPersonageMention.of(acceptorPersonage, acceptorUser.id())))
                                .build()
                        );
                    });
        } else {
            return duelService.declineDuel(duel, clickerId)
                    .peek(success -> {
                        final var initiatingPersonage = personageService.getByIdForce(duel.initiatingPersonageId());
                        final var initiatingUser = userService.getByPersonageIdForce(duel.initiatingPersonageId());
                        telegramSender.send(EditMessageTextBuilder.builder()
                                .chatId(group.id())
                                .messageId(command.messageId())
                                .text(DuelLocalization.declinedDuel(group.language(),
                                        TgPersonageMention.of(initiatingPersonage, initiatingUser.id())))
                                .build()
                        );
                    });
        }
    }
}
