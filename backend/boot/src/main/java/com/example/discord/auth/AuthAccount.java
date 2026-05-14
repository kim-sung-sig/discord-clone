package com.example.discord.auth;

import com.example.discord.identity.EmailAddress;
import com.example.discord.user.UserProfile;

record AuthAccount(EmailAddress email, String passwordHash, UserProfile profile) {
}
