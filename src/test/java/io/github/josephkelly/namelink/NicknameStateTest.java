package io.github.josephkelly.namelink;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class NicknameStateTest {
    @TempDir Path dir;
    UUID alex=UUID.randomUUID(), ben=UUID.randomUUID();
    NicknameState state;
    @BeforeEach void setup() { state=new NicknameState(); state.observe(alex,"BlockMaster42",true); state.observe(ben,"VolleyBen",false); }
    @Test void format() {
        for(String good:List.of("Alex","a_3","123","Abcdefghijklmnop")) assertTrue(NicknameState.valid(good));
        for(String bad:List.of("ab","abcdefghijklmnopq","Álex","A lex","§Alex","@a","@p[name=Alex]",alex.toString(),"","Alex/","Alex\n")) assertFalse(NicknameState.valid(bad),bad);
    }
    @Test void legacyShortAccountNamesCanReceiveNicknames() {
        UUID legacy=UUID.randomUUID();state.observe(legacy,"ab",true);state.assign(legacy,"Legacy");
        assertEquals(legacy,state.resolve("ab"));assertEquals(legacy,state.resolve("Legacy"));
    }
    @Test void copyPreservesPendingStartupConflictNotices() {
        state.assign(alex,"Alex");state.observe(ben,"Alex",true);assertEquals(1,state.copy().drainNotices().size());
    }
    @Test void ownUsernameRejected() { assertThrows(IllegalArgumentException.class,()->state.assign(alex,"blockmaster42")); }
    @Test void otherUsernameRejected() { assertThrows(IllegalArgumentException.class,()->state.assign(alex,"vOLLEYbEN")); }
    @Test void caseInsensitiveUniqueness() { state.assign(alex,"Alex"); assertThrows(IllegalArgumentException.class,()->state.assign(ben,"aLEX")); }
    @Test void noopAndCaseChange() { state.assign(alex,"Alex"); assertFalse(state.assign(alex,"Alex")); assertTrue(state.assign(alex,"ALEX")); assertEquals("ALEX",state.identity(alex).nickname()); }
    @Test void offlineKnownAndWhitelistIdentities() { assertEquals(ben,state.resolve("VOLLEYBEN")); state.observe(UUID.randomUUID(),"Whitelisted",false); assertThrows(IllegalArgumentException.class,()->state.assign(alex,"whitelisted")); }
    @Test void loginConflictDisablesNickname() {
        state.assign(alex,"Alex"); UUID joining=UUID.randomUUID(); state.observe(joining,"aLEX",true);
        assertNull(state.identity(alex).nickname()); assertEquals(joining,state.resolve("Alex")); assertEquals(1,state.drainNotices().size());
    }
    @Test void ownUsernameChangeDisablesConflictingNickname() { state.assign(alex,"Alex"); state.observe(alex,"Alex",true); assertNull(state.identity(alex).nickname()); assertEquals(alex,state.resolve("Alex")); }
    @Test void usernameChangeReservesHistoryWithoutRedirects() {
        state.observe(alex,"NewAccount",true); assertNull(state.resolve("BlockMaster42"));
        assertThrows(IllegalArgumentException.class,()->state.assign(ben,"BlockMaster42")); assertEquals(alex,state.resolve("NewAccount"));
    }
    @Test void authoritativeCurrentNameWinsOverHistoricalCache() { state.observe(alex,"OldCacheName",false); assertEquals("BlockMaster42",state.identity(alex).username()); assertNull(state.resolve("OldCacheName")); }
    @Test void uuidStableAcrossChangesAndRemoval() {
        state.assign(alex,"Alex"); assertEquals(alex,state.resolve("aLeX")); assertEquals(alex,state.resolve("BlockMaster42"));
        state.assign(alex,"Captain"); assertNull(state.resolve("Alex")); assertEquals(alex,state.resolve("Captain"));
        state.clear(alex); assertNull(state.resolve("Captain")); assertEquals(alex,state.identity(alex).id());
    }
    @Test void retiredAliasesNeverTransfer() { state.assign(alex,"Alex"); state.clear(alex); assertThrows(IllegalArgumentException.class,()->state.assign(ben,"Alex")); assertTrue(state.assign(alex,"Alex")); }
    @Test void realUsernamesOverrideRetiredAliases() { state.assign(alex,"Alex"); state.clear(alex); state.observe(ben,"Alex",true); assertEquals(ben,state.resolve("Alex")); }
    @Test void ambiguousOfflineCurrentNamesRefused() { state.observe(ben,"BlockMaster42",true); assertNull(state.resolve("BlockMaster42")); }
    @Test void selectorAndUuidAreNotAliases() { state.assign(alex,"Alex"); for(String s:List.of("@a","@a[name=Alex]",alex.toString(),"Alex/")) assertNull(state.resolve(s)); }
    @Test void restartRetainsIdentityHistoryAndReservations() throws Exception {
        state.assign(alex,"Alex"); state.assign(alex,"Captain"); state.observe(alex,"NewAccount",true);
        var store=new StateStore(dir.resolve("data/namelink.json")); store.save(state); var loaded=store.load();
        assertEquals(alex,loaded.resolve("Captain")); assertEquals(alex,loaded.resolve("NewAccount"));
        assertNull(loaded.resolve("BlockMaster42")); assertThrows(IllegalArgumentException.class,()->loaded.assign(ben,"Alex"));
        assertTrue(Files.list(dir.resolve("data")).allMatch(p->p.getFileName().toString().equals("namelink.json")));
    }
    @Test void absentFileStartsEmpty() throws Exception { assertTrue(new StateStore(dir.resolve("absent.json")).load().identities().isEmpty()); }
    @Test void malformedOrIncompatibleStorageNeverOverwritten() throws Exception {
        Path path=dir.resolve("namelink.json"); var store=new StateStore(path);
        for(String bad:List.of("", "{", "null", "{}", "{\"version\":2}","{\"version\":1,\"identities\":[],\"reservations\":{\"Alex\":\"bad\"}}")) {
            Files.writeString(path,bad); assertThrows(java.io.IOException.class,store::load); assertEquals(bad,Files.readString(path));
        }
    }
    @Test void duplicateJsonKeysAndLenientSyntaxAreRejected() throws Exception {
        Path path=dir.resolve("state.json");var store=new StateStore(path);
        for(String text:List.of("{\"version\":1,\"version\":1,\"identities\":[],\"reservations\":{}}", "{version:1,identities:[],reservations:{}}", "{\"version\":1,\"identities\":[],\"reservations\":{}} trailing")) {
            Files.writeString(path,text);assertThrows(java.io.IOException.class,store::load);assertEquals(text,Files.readString(path));
        }
    }
    @Test void schemaTypesAndUnknownFieldsAreRejected() throws Exception {
        Path path=dir.resolve("state.json");var store=new StateStore(path);store.save(state);String valid=Files.readString(path);
        for(String text:List.of(valid.replace("\"version\": 1","\"version\": \"1\""),valid.replace("\"BlockMaster42\"","123"),valid.replace("\"version\": 1","\"unknown\": true, \"version\": 1"))) {
            Files.writeString(path,text);assertThrows(java.io.IOException.class,store::load);
        }
    }
    @Test void configDefaultsAndMalformedFlags() throws Exception {
        Path path=dir.resolve("config/namelink.json");var defaults=NameLinkConfig.load(path);
        assertTrue(defaults.chat() && defaults.tab() && defaults.overhead() && defaults.vanillaPlayerArguments() && defaults.quickLock());
        for(String text:List.of("{\"chat\":true,\"chat\":false}","{\"chat\":\"true\"}","{\"unknown\":true}")) {
            Files.writeString(path,text);assertThrows(java.io.IOException.class,()->NameLinkConfig.load(path));
        }
    }
    @Test void invalidRecordsAndDuplicateAssignmentsFail() {
        var a=new NicknameState.Identity(alex,"BlockMaster42",Set.of("BlockMaster42"),"Alex");
        var b=new NicknameState.Identity(ben,"VolleyBen",Set.of("VolleyBen"),"ALEX");
        assertThrows(IllegalArgumentException.class,()->NicknameState.restore(List.of(a,b),Map.of("alex",alex)));
        assertThrows(IllegalArgumentException.class,()->NicknameState.restore(List.of(a,a),Map.of("alex",alex)));
        assertThrows(IllegalArgumentException.class,()->NicknameState.restore(List.of(a),Map.of("alex",ben)));
        assertThrows(IllegalArgumentException.class,()->NicknameState.restore(List.of(new NicknameState.Identity(alex,"x",Set.of(),null)),Map.of()));
    }
    @Test void startupConflictDisabledWithNotice() {
        var a=new NicknameState.Identity(alex,"BlockMaster42",Set.of("BlockMaster42"),"Alex");
        var b=new NicknameState.Identity(ben,"Alex",Set.of("Alex"),null);
        var loaded=NicknameState.restore(List.of(a,b),Map.of("alex",alex));
        assertNull(loaded.identity(alex).nickname()); assertEquals(ben,loaded.resolve("Alex")); assertFalse(loaded.drainNotices().isEmpty());
    }
    @Test void failedSaveLeavesPreviousSnapshot() throws Exception {
        Path path=dir.resolve("state.json"); var store=new StateStore(path); store.save(state); String before=Files.readString(path);
        var next=state.copy(); next.assign(alex,"Alex");
        // A parent which is a regular file cannot receive a temporary file.
        assertThrows(java.io.IOException.class,()->new StateStore(path.resolve("child.json")).save(next));
        assertEquals(before,Files.readString(path)); assertNull(store.load().identity(alex).nickname());
    }
}
