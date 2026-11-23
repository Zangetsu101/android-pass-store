# Encrypt and push login edits

The Android client writes a login edit as an encrypted file, commits the
selected path with the imported key's first UID, and pushes from the app's
background scope. The local commit is the save boundary. The entry detail can
show the saved content immediately, and a failed push leaves the commit
available for the manual sync recovery flow. A pending commit exists only
in the clone until GitHub accepts it. The backup policy in
[ADR 0002](0002-threat-model.md) still applies.

Each entry inherits recipients from its nearest `.gpg-id`. The app encrypts
only when every recipient resolves to the imported key, because overwriting
an entry in a shared folder with ciphertext for one recipient would cut off
everyone else. The app uses the public certificate and encrypts to subkeys
whose private material is present locally, so saving an already open draft
works after the decryption session ends. The draft stays in memory, and the
working copy and Git history receive only ciphertext.

Clone, sync, commit, push, and discard share a lock. The write checks the current
ciphertext against the form's original version while holding the lock so a
pull cannot cause the form to overwrite a newer version silently.

Manual sync fetches the tracked branch, rebases unpushed commits if the remote
advanced, then pushes. This keeps local edits when the two histories changed
different entries. When both histories change the same encrypted entry, sync
cannot combine the ciphertexts safely. Sync keeps the local commits and
reports the conflict. The recovery panel shows each entry change that the
reset removes before the user confirms the reset to the fetched remote branch.
