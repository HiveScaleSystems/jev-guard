package dev.jevguard.paper;

import dev.jevguard.core.JevGuard;
import dev.jevguard.core.Permissions;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Collection;

final class GuardCommand implements BasicCommand {
    private final JevGuard guard;

    GuardCommand(JevGuard guard) {
        this.guard = guard;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        guard.commands().execute(source.getSender(), new PaperAudience(source.getSender()), args);
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        return guard.commands().suggest(args);
    }

    @Override
    public String permission() {
        return Permissions.ADMIN;
    }
}
