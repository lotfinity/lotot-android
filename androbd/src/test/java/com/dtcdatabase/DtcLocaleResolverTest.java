package com.dtcdatabase;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;
public class DtcLocaleResolverTest {
    private DtcLocaleResolver catalog() throws Exception {
        return new DtcLocaleResolver(new JSONObject("{\"source_en\":{\"P0301\":\"Cylinder 1 Misfire Detected\"},\"translations\":{\"fr\":{\"P0301\":\"Ratés cylindre 1\"},\"ar-DZ\":{\"P0301\":\"Ratés في cylindre 1\"}}}"));
    }
    private DtcLocaleResolver.Definition english(String manufacturer) {
        return new DtcLocaleResolver.Definition("P0301","Cylinder 1 Misfire Detected","P",manufacturer,"en");
    }
    @Test public void localeNormalizationAndFallbackAreExplicit() {
        assertEquals("fr",DtcLocaleResolver.normalize("fr_CA"));
        assertEquals("ar-DZ",DtcLocaleResolver.normalize("ar_DZ"));
        assertEquals("en",DtcLocaleResolver.normalize("tr-TR"));
        assertArrayEquals(new String[]{"ar-DZ","fr","en"},DtcLocaleResolver.chain("ar-DZ"));
    }
    @Test public void genericDefinitionsTranslateOnlyTheirExactSource() throws Exception {
        DtcLocaleResolver.Source source=(code,make,locale)->"GENERIC".equals(make)&&"en".equals(locale)?english(make):null;
        assertEquals("Ratés cylindre 1",catalog().resolve(" p0301 ",null,"fr",source).description);
        assertEquals("ar-DZ",catalog().resolve("P0301","GENERIC","ar-DZ",source).locale);
        assertEquals("Cylinder 1 Misfire Detected",catalog().resolve("P0301",null,"en",source).description);
        DtcLocaleResolver.Source changed=(c,m,l)->"en".equals(l)?new DtcLocaleResolver.Definition(c,"Changed source","P",m,l):null;
        assertEquals("Changed source",catalog().resolve("P0301",null,"fr",changed).description);
    }
    @Test public void manufacturerSpecificEvidenceBeatsGenericTranslation() throws Exception {
        DtcLocaleResolver.Source source=(code,make,locale)->"en".equals(locale)?english(make):null;
        DtcLocaleResolver.Definition result=catalog().resolve("P0301","AUDI","fr",source);
        assertEquals("AUDI",result.manufacturer);
        assertEquals("en",result.locale);
        assertEquals("Cylinder 1 Misfire Detected",result.description);
    }
    @Test public void storedTranslationAndEnglishLongTailRemainAvailable() throws Exception {
        DtcLocaleResolver.Source source=(c,m,l)->"fr".equals(l)?new DtcLocaleResolver.Definition(c,"Stored French","P",m,l):"en".equals(l)?english(m):null;
        assertEquals("Stored French",catalog().resolve("P0301",null,"fr",source).description);
        DtcLocaleResolver.Source englishOnly=(c,m,l)->"en".equals(l)?new DtcLocaleResolver.Definition(c,"Rare original definition","P",m,l):null;
        assertEquals("Rare original definition",catalog().resolve("P2999",null,"ar-DZ",englishOnly).description);
        assertNull(catalog().resolve("P9999",null,"fr",(c,m,l)->null));
    }
}
