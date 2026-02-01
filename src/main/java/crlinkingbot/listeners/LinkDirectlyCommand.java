package crlinkingbot.listeners;

import crlinkingbot.services.LostCRManagerClient;
import crlinkingbot.util.MessageUtil;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.json.JSONObject;

/**
 * Command listener for directly linking a Discord user to a player tag.
 */
public class LinkDirectlyCommand extends ListenerAdapter {

    // Allowed role IDs (Staff)
    private static final String ROLE_ID_1 = "1404574565350506587";
    private static final String ROLE_ID_2 = "1108472754149281822";

    @SuppressWarnings("null")
    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("linkdirectly")) {
            return;
        }

        event.deferReply().queue();

        new Thread(() -> {
            String title = "Account Direkt-Verlinkung";

            // Check if user has required role
            Member executingMember = event.getMember();
            if (executingMember == null) {
                event.getHook().editOriginalEmbeds(MessageUtil.createErrorEmbed(title,
                        "Dieser Befehl kann nur auf einem Server ausgeführt werden.")).queue();
                return;
            }

            boolean hasPermission = executingMember.getRoles().stream().map(Role::getId)
                    .anyMatch(roleId -> roleId.equals(ROLE_ID_1) || roleId.equals(ROLE_ID_2));

            if (!hasPermission) {
                event.getHook().editOriginalEmbeds(
                        MessageUtil.createErrorEmbed(title, "Du hast keine Berechtigung, diesen Befehl auszuführen."))
                        .queue();
                return;
            }

            // Get parameters
            User targetUser = event.getOption("user", OptionMapping::getAsUser);
            String playerTag = event.getOption("tag", OptionMapping::getAsString);
            OptionMapping nopingOption = event.getOption("noping");
            boolean ping = nopingOption == null;

            if (targetUser == null || playerTag == null) {
                event.getHook().editOriginalEmbeds(MessageUtil.createErrorEmbed(title, "Parameter fehlen.")).queue();
                return;
            }

            // Call lostcrmanager API
            String userId = targetUser.getId();
            JSONObject linkResult = LostCRManagerClient.linkPlayer(playerTag, userId);

            if (linkResult.getBoolean("success")) {
                String playerName = linkResult.getJSONObject("data").getString("playerName");
                String successEmbedMsg = String.format(
                        "Account wurde erfolgreich verknüpft!\n\n**Spieler-Name:** `%s`\n"
                                + "**Spieler-Tag:** `%s`\n" + "**Discord User:** <@%s>",
                        playerName, playerTag, userId);

                event.getHook().editOriginalEmbeds(MessageUtil.createSuccessEmbed("Account verknüpft", successEmbedMsg))
                        .queue();

                // Send follow-up message to channel
                MessageChannelUnion channel = event.getChannel();
                String successMessage = ping ? "Hallo <@" + userId + ">,\r\n"
                        + "die Verlinkung mit unserem **Tracking-Bot** wurde erfolgreich abgeschlossen!\r\n"
                        + "\r\n"
                        + "Du befindest dich jetzt **in unserer Warteschlange** für den Clanbeitritt.\r\n"
                        + "Die Reihenfolge des Beitritts und der Clan, dem wir dich zuordnen werden, richten sich nach deiner **Leistung im Ranked** – diese hat **Priorität vor den Trophäen**.\r\n"
                        + "Zudem fließt unsere Einschätzung mit ein.\r\n"
                        + "Es lohnt sich also, weiter zu **grinden**, um deine Chancen zu erhöhen. <:Peepo_Stonks:1312189892008087563>\r\n"
                        + "\r\n"
                        + "Sobald du **in einem unserer Clans bist**, greift unser **internes Auf- und Abstiegssystem**. Dieses wird **zu Beginn jeder Season** angewendet und basiert auf **deiner Leistung der beendeten Season**. Dadurch sind innerhalb der Clan-Family **Auf- und Abstiege** zwischen den Clans möglich.\r\n"
                        + "\r\n"
                        + "Sobald du für einen Clanplatz ausgewählt wirst, **melden wir uns wieder bei dir**.\r\n"
                        + "Das kann **schon bald**, je nach Aktivität der anderen Bewerber aber auch **etwas länger dauern**.\r\n"
                        + "\r\n" + "Bleib aktiv und viel Erfolg beim Pushen!\r\n" + "LG die CR-Vize"
                        : "Verlinkung eingereicht.";

                channel.sendMessage(successMessage).queue(msg -> {
                    if (!ping) {
                        msg.delete().queueAfter(10, java.util.concurrent.TimeUnit.SECONDS);
                    }
                });

            } else {
                String errorMsg = "Es gab einen Fehler beim Verknüpfen des Accounts.";
                if (linkResult.has("data") && linkResult.getJSONObject("data").has("message")) {
                    errorMsg += "\n\n**Fehler:** " + linkResult.getJSONObject("data").getString("message");
                } else if (linkResult.has("error")) {
                    errorMsg += "\n\n**Fehler:** " + linkResult.getString("error");
                }
                event.getHook().editOriginalEmbeds(MessageUtil.createErrorEmbed("Verknüpfung fehlgeschlagen", errorMsg))
                        .queue();
            }

        }, "LinkDirectlyCommand-" + event.getUser().getId() + "-" + System.currentTimeMillis()).start();
    }
}
