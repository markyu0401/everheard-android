# Legal notes

> This is engineering guidance, not legal advice. Recording law varies by country, state, and situation. Consult a lawyer before distributing or using this app around other people.

## The short version

- In **one-party-consent** jurisdictions, a person who is part of a conversation may record it without telling the others. Indiana (US) is one-party.
- In **all-party-consent** jurisdictions, every participant must consent. Several US states and many countries fall here.
- Recording a conversation **you are not part of** — bystanders in a lift, colleagues at the next desk, a private call you overhear — is the risky case everywhere. One-party consent does not cover it.
- Transcripts are personal data about other people. Storing and exporting them can trigger privacy-law obligations even where the recording itself was lawful.

## Product rules that follow from this

1. **Capture is visible.** A persistent notification, a live screen, and a start tone. No covert mode, no hidden icon, no "stealth" setting.
2. **Default to session mode**, not always-on, on first install. Always-on is an explicit opt-in with a schedule.
3. **Meeting mode announcement.** When enabled, the app plays a short tone and shows a full-screen banner at the start of a session: "Recording — transcript only, audio deleted."
4. **Public-place degradation.** If the user marks a session as "public", the app pauses automatically after a short window and asks whether to continue. It does not silently record a crowd.
5. **Bystander handling.** Low-confidence, non-participant speech is folded into a "background" bucket, not attributed to the user. The user can delete it in one action.
6. **No covert distribution.** The README and the first-run screen state the legal responsibility. A fork that removes the visible indicators is not supported.

## Jurisdiction notes

| Place | Rule | Practical consequence |
|---|---|---|
| Indiana, US | One-party consent | You may record conversations you are part of. Bystanders are not covered. |
| California, Florida, Illinois, Maryland, Massachusetts, Montana, New Hampshire, Oregon, Pennsylvania, Washington, Connecticut (US) | All-party consent (with exceptions) | Ask before recording; meeting-mode announcement is mandatory. |
| China | Privacy / personal-information rules protect others' speech; recording without consent can be unlawful | Do not record people who have not agreed; a visible indicator and announcement are the minimum. |
| EU / UK | GDPR + local interception rules | Consent or a lawful basis; transcripts of identifiable people are personal data; deletion rights apply. |

The list above is a starting point, not a complete survey. Check the law where you will use the app.

## Open-source responsibility

The maintainers provide software, not a licence to record people unlawfully. Users are responsible for consent, retention, and access. Issues or PRs that add covert recording, hidden indicators, or automatic upload without consent will be rejected.
