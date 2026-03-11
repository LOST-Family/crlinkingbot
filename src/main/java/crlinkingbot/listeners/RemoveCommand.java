package crlinkingbot.listeners;

import crlinkingbot.services.LostCRManagerClient;
import crlinkingbot.util.MessageUtil;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Command listener for removing a player's clan membership and Discord link.
 */
public class RemoveCommand extends ListenerAdapter {

    // Allowed role IDs (same as other commands)
    private static final String ROLE_ID_1 = "1404574565350506587";
    private static final String ROLE_ID_2 = "1108472754149281822";

    @SuppressWarnings("null")
    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("remove")) {
            return;
        }

        event.deferReply().queue();

        new Thread(() -> {
            String title = "CR Account Entfernen";

            // Check if user has required role
            Member member = event.getMember();
            if (member == null) {
                event.getHook().editOriginalEmbeds(MessageUtil.createErrorEmbed(title,
                        "Dieser Befehl kann nur auf einem Server ausgeführt werden.")).queue();
                return;
            }

            boolean hasPermission = member.getRoles().stream().map(Role::getId)
                    .anyMatch(roleId -> roleId.equals(ROLE_ID_1) || roleId.equals(ROLE_ID_2));

            if (!hasPermission) {
                event.getHook().editOriginalEmbeds(
                        MessageUtil.createErrorEmbed(title, "Du hast keine Berechtigung, diesen Befehl auszuführen."))
                        .queue();
                System.out.println(
                        "User " + event.getUser().getAsTag() + " attempted to use remove command without permission");
                return;
            }

            OptionMapping tagOption = event.getOption("player");
            if (tagOption == null) {
                event.getHook()
                        .editOriginalEmbeds(
                                MessageUtil.createErrorEmbed(title, "Der Parameter `player` ist erforderlich."))
                        .queue();
                return;
            }

            String playerTag = tagOption.getAsString();

            // Call lostcrmanager API to remove the player
            JSONObject removeResult = LostCRManagerClient.removePlayer(playerTag);

            if (removeResult.getBoolean("success")) {
                JSONObject data = removeResult.optJSONObject("data");
                String playerName = data != null ? data.optString("playerName", playerTag) : playerTag;
                String successMsg = String.format(
                        "Spieler wurde erfolgreich entfernt!\n\n**Spieler-Name:** `%s`\n**Spieler-Tag:** `%s`",
                        playerName, playerTag);
                event.getHook()
                        .editOriginalEmbeds(MessageUtil.createSuccessEmbed("Spieler entfernt", successMsg))
                        .queue();
            } else {
                String errorMsg = "Es gab einen Fehler beim Entfernen des Spielers.";
                JSONObject data = removeResult.optJSONObject("data");
                if (data != null && data.has("error")) {
                    errorMsg += "\n\n**Fehler:** " + data.getString("error");
                } else if (removeResult.has("error")) {
                    errorMsg += "\n\n**Fehler:** " + removeResult.getString("error");
                }
                event.getHook()
                        .editOriginalEmbeds(MessageUtil.createErrorEmbed("Entfernen fehlgeschlagen", errorMsg))
                        .queue();
            }

        }, "RemoveCommand-" + event.getUser().getId() + "-" + System.currentTimeMillis()).start();
    }

    @SuppressWarnings("null")
    @Override
    public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
        if (!event.getName().equals("remove")) {
            return;
        }

        if (!"player".equals(event.getFocusedOption().getName())) {
            return;
        }

        String query = event.getFocusedOption().getValue();

        JSONObject searchResult = LostCRManagerClient.searchLinkedPlayers(query);

        List<Choice> choices = new ArrayList<>();
        if (searchResult.getBoolean("success")) {
            JSONObject data = searchResult.optJSONObject("data");
            if (data != null && data.has("players")) {
                JSONArray players = data.getJSONArray("players");
                for (int i = 0; i < players.length() && choices.size() < 25; i++) {
                    JSONObject player = players.getJSONObject(i);
                    String tag = player.getString("tag");
                    String display = player.optString("display", tag);
                    // Discord choice value max 100 chars, display max 100 chars
                    if (display.length() > 100) {
                        display = display.substring(0, 100);
                    }
                    choices.add(new Choice(display, tag));
                }
            }
        }

        event.replyChoices(choices).queue();
    }
}
