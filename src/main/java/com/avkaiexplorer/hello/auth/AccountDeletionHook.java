package com.avkaiexplorer.hello.auth;

/**
 * Implement this (as a Spring bean) to erase the app's own data about a person. It runs when the person uses the app's
 * "Delete my account" button, just before they are sent to Keycloak to delete their login. Hooks must be idempotent.
 */
public interface AccountDeletionHook {

	void deleteDataOf(String subject);

}
