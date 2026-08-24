package com.zuhlke.tradenet.chat.controller;

import com.zuhlke.tradenet.chat.auth.AppUserPrincipal;
import com.zuhlke.tradenet.chat.repository.UserRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Comparator;
import java.util.List;

@Controller
public class ChatController {

    private final UserRepository userRepository;

    public ChatController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public record UserSummary(Long id,String username){

    }

    @GetMapping("/chat")
    public String chat(@RequestParam(required = false) Long with, @AuthenticationPrincipal AppUserPrincipal principal,
                            Model model)   {
        List<UserSummary> otherUsers = userRepository.findAll().stream().filter(user -> !user.getId().equals(principal.getUserId()))
                .map(user -> new UserSummary(user.getId(), user.getUsername()))
                .sorted(Comparator.comparing(UserSummary::username))
                .toList();

        boolean validSelection = with != null && otherUsers.stream().anyMatch( otherUser -> otherUser.id().equals(with));

        model.addAttribute("others", otherUsers);
        model.addAttribute("myId", principal.getUserId());
        model.addAttribute("myUsername", principal.getUsername());
        model.addAttribute("activeWith", validSelection ? with : null);
        return "chat";
    }
}

