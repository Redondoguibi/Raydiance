package com.redondoguibi.raydiance.client.profile;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
class ProfileRulesTest {
    private ProfileRules.Rule rule(String name,int start,int end,int priority){return new ProfileRules.Rule(name,"minecraft:overworld","minecraft:forest","rain",start,end,priority,Map.of());}
    private ProfileRules.Context context(int time){return new ProfileRules.Context("minecraft:overworld","minecraft:forest","rain",time);}
    @Test void matchesAllSelectorsTogether(){var r=rule("A",0,0,0);assertTrue(r.matches(context(1)));assertFalse(r.matches(new ProfileRules.Context("minecraft:the_nether","minecraft:forest","rain",1)));assertFalse(r.matches(new ProfileRules.Context("minecraft:overworld","minecraft:desert","rain",1)));assertFalse(r.matches(new ProfileRules.Context("minecraft:overworld","minecraft:forest","clear",1)));}
    @Test void wrapsMidnightWithExclusiveEnd(){var r=rule("Night",23000,1000,0);assertTrue(r.matches(context(23000)));assertTrue(r.matches(context(0)));assertFalse(r.matches(context(1000)));assertFalse(r.matches(context(12000)));}
    @Test void resolvesPriorityAndTieDeterministically(){var a=rule("A",0,0,5);var b=rule("B",0,0,5);var low=rule("C",0,0,1);assertEquals(a,ProfileRules.select(List.of(low,b,a),context(0)));}
    @Test void supportsWildcardsAndNoMatch(){var r=new ProfileRules.Rule("Any","*","*","*",0,0,0,Map.of());assertTrue(r.matches(context(12000)));assertNull(ProfileRules.select(List.of(rule("No",100,200,0)),context(500)));}
    @Test void smoothsWithoutOvershootAndSnapsWhenDisabled(){assertEquals(1,ProfileRules.blend(0,1,0,.05));double v=ProfileRules.blend(0,1,3,.05);assertTrue(v>0&&v<1);assertEquals(v,ProfileRules.blend(0,1,3,.05));assertEquals(0,ProfileRules.blend(0,1,3,0));}
    @Test void rejectsMalformedProfileValues(){assertThrows(IllegalArgumentException.class,()->DynamicProfiles.parse("{\"profiles\":[{\"name\":\"Bad\",\"values\":{\"bloom\":2}}]}"));assertThrows(IllegalArgumentException.class,()->DynamicProfiles.parse("{\"profiles\":[{\"name\":\"Bad\",\"start\":24000,\"values\":{}}]}"));}
    @Test void readsMaterialControlsInDynamicProfiles(){
        var rules=DynamicProfiles.parse("""
            {"profiles":[{"name":"Wet","weather":"rain","values":{"materialWetness":0.8,"materialSpecular":0.3,"materialEmissive":0.2,"waterAbsorption":0.4}}]}
            """);
        assertEquals(.8,rules.getFirst().values().get("materialWetness"));
        assertEquals(4,rules.getFirst().values().size());
    }
    @Test void readsCompleteRule(){var rules=DynamicProfiles.parse("{\"profiles\":[{\"name\":\"Night rain\",\"weather\":\"rain\",\"start\":13000,\"end\":23000,\"priority\":10,\"values\":{\"exposure\":-0.2,\"bloom\":0.1}}]}");assertEquals(2,rules.getFirst().values().size());assertEquals("*",rules.getFirst().dimension());}
}
