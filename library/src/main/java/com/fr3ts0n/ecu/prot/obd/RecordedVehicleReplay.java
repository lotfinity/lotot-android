/*
 * Selected ordered records from DieselOBD MASTER_TEST_X.xlsx; source CC BY 4.0.
 * Karim Lounis, Hadjer Mekki, Wissam Hadjab, DieselOBD (2025).
 * https://github.com/AbouAbdallah-Lounis/OBD-Dataset
 * Modification: 320 evenly spaced samples per DTC-labeled contiguous block,
 * serialized as gzip+Base64. Time is NOT available: 500ms is simulated playback.
 * Raw units are ambiguous in the public README; inferred conversions are
 * explicit in DemoVehicleModel and documented in docs/DIESELOBD_REPLAY.md.
 * The unlabeled healthy block is not assigned to any make or model.
 */
package com.fr3ts0n.ecu.prot.obd;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

final class RecordedVehicleReplay {
    static final long FRAME_MS = 500L; // invented playback cadence, not captured timestamps
    static final class Profile {
        final String id, manufacturer, model, engine, marker;
        final int year;
        final String[] codes;
        final double[][] rows;
        Profile(String id,String manufacturer,String model,int year,String engine,String marker,String codes,String blob) {
            this.id=id;this.manufacturer=manufacturer;this.model=model;this.year=year;
            this.engine=engine;this.marker=marker;this.codes=codes.isEmpty()?new String[0]:codes.split(",");
            this.rows=unpack(blob);
        }
        double[] frame(long elapsedMs) {
            int index=(int)((Math.max(0,elapsedMs)/FRAME_MS)%rows.length);
            return rows[index];
        }
    }
    // java.util.Base64 is API 26+. This compact decoder preserves LoToTi's
    // Android minSdk 23 compatibility without introducing external libraries.
    private static byte[] decodeBase64(String source) {
        ByteArrayOutputStream output = new ByteArrayOutputStream(source.length() * 3 / 4);
        int bits = 0, buffer = 0;
        for (int i=0;i<source.length();i++) {
            char c=source.charAt(i);
            if(c=='=')break;
            int value;
            if(c>='A' && c<='Z')value=c-'A';
            else if(c>='a' && c<='z')value=c-'a'+26;
            else if(c>='0' && c<='9')value=c-'0'+52;
            else if(c=='+')value=62;
            else if(c=='/')value=63;
            else throw new IllegalArgumentException("Invalid Base64 replay content");
            buffer=(buffer<<6)|value;
            bits+=6;
            if(bits>=8) {
                bits-=8;
                output.write((buffer>>bits)&0xFF);
            }
        }
        return output.toByteArray();
    }

    private static double[][] unpack(String blob) {
        try {
            byte[] packed=decodeBase64(blob);
            GZIPInputStream in=new GZIPInputStream(new ByteArrayInputStream(packed));
            ByteArrayOutputStream buffer=new ByteArrayOutputStream();
            byte[] block=new byte[4096];int n;
            while((n=in.read(block))!=-1)buffer.write(block,0,n);
            in.close();
            String[] records=new String(buffer.toByteArray(),StandardCharsets.US_ASCII).trim().split("\n");
            double[][] rows=new double[records.length][];
            for(int i=0;i<records.length;i++){
                String[] parts=records[i].split(",",-1);
                if(parts.length!=11)throw new IllegalArgumentException("Bad replay row " +i);
                double[] values=new double[11];
                for(int j=0;j<11;j++)values[j]=Double.parseDouble(parts[j]);
                rows[i]=values;
            }
            if(rows.length==0)throw new IllegalArgumentException("Empty replay");
            return rows;
        } catch(Exception e){throw new IllegalStateException("Corrupt bundled DieselOBD replay",e);}
    }
    private static final Map<String,Profile> PROFILES=new HashMap<>();
    private static void register(String id,String manufacturer,String model,int year,String engine,String marker,String codes,String blob){
       PROFILES.put(id,new Profile(id,manufacturer,model,year,engine,marker,codes,blob));
    }
    static Profile find(String id){return PROFILES.get(id);}
    static {
        register("replay_peugeot_expert_2014","Peugeot","Expert",2014,"2.0 HDi","PEXPRT","P0406,P0234,P0102,P0113,P1351",
            "H4sIAAAAAAAC/+XbW3bcIAwA0P+uZc4cGRCC/W+sMx63NQ9R4YAtJz95Tm5OCAIhYROfDh5LdE94vXPPCA/C5f0JvN9E2j60D4uGnsv6IrO+9c" +
            "sjvl/5y3QY6M81HOk2HGw/nRvh6acY8QJjfb3MMPikvQEtw9/WcFIjn2MwIF6uMpxlDZhikHKD1rH7Y2CcbDh3poGwGUFqWNheJDAQv7+RzbH2" +
            "mAblRro37I11/fAHjNAynG7jFc/+TAO9buP1N6LYgB9ghPe3vxgvSgx0SR60N2CK4aXG+ttON2w42SDdhlvS9WNnyOepfkM818v9tn9+aDGMWX" +
            "ME2ZgOMNDoNjwx8UKfXGq0wY2pFiMgO8eCNHe4gSHPpeBpxQbpNpzPz0CTDXOaUdQ/ROtHSPaGf0ZYvzLcYNdTJUYwab0wr6EkBp5mII4xqNew" +
            "Js21WwZamREbf8tUg/L1I60njzbYmpISo8yl/o5pmGGg765bnmsU9aD+vbLHcPYSI0gNsmnNoJl/ON1GGS+N/+0Ag11PlRiNXCrMMLiakhajkY" +
            "9NMdh+5VwDpAbGDsPqNti1sLZXjjC8bmMh+dljiBF0G1kfuTDCYAPpAmM7A0mM7BzVNLh4UW9s64fE+F/NMfTfEbjMIJ/3xD+Ghc/6MdxA3Ube" +
            "S9qdb90Uw0h7Wo3Y7zeC1IggN7jeyQ0MkBqvGAhfXT+UGB6Wdj15tMHEixaD3ys/d0qHG8L7dGONWg7jj/Uag6QepN8AqdHonYA0L9RiGNc+Nw" +
            "w3UGzQFUalZkDsmA4wOnprwwwLlZyf63vYom/KGuw8VW70xEvZb2gYpNuo1Ax+tFG5MzHXCJcY8tpFWWPjDXNbI0gNb4tzJfWePbQYp891p9uo" +
            "1FCIrSmNMKJuw0PRW+Nj7mZGOGBQTHOplsHWx5QY+fMvIVs/4mCDvTuqxMjrQeHAfttlON1GpX7Kzw+4rQFSI3uWuGmwe6USo1IvTM7Iw42g28" +
            "ifCTiyfhw1YtuIVxh5DWVnwBQDLzGc1MhrKC1jv35oNCp75VRjv7+oNLI8ebaByo38OeDW+jHC4GJOiwE+3V/qxlqET84NIwxqG9RrBGANGGv8" +
            "p3dSGniFgbbYG1AS+0cNVG7kzwT4yYZTbuT3g+pGO15uYIhjP687+EbMsfe1lRh5r4A2wy4/0yCT5g505P9SGtSbr1eMeIVhXZpbtoz9falbGZ" +
            "UzMmfk56imAbqN/NzQHNMRBooNusLIn4umRg1lhLG/PzbbaOanjEFYnBuot/5xA8NJjejZMb2b4SGwvRMYa2y5lP0ORjNeugxmPZ1rdOwN7Pk2" +
            "MbYxpfON3860AosAVQAA");
        register("replay_chevrolet_captiva_2011","Chevrolet","Captiva",2011,"2.0 Diesel","CAPTIV","P0107,P0069,P0089",
            "H4sIAAAAAAAC/7WbW3IcKwxA/+9aXFMgAYL9b+wComkBood2kp+Undgn6P2AsfHjzI917mPMD3xi/tqDLd8gfij9hFi+Nh/jf5Iz8MHyw/wrH7" +
            "A/DvM//xf4r29GCr5+bT4JbgT+RPD+YzWEDfz3cJ/DYP1FcB/rHiGWDiFkOsQnnwZhLP64cuj//CeNwkSsv2bpQ+YnhGeErQgsPzoqtX0DH48S" +
            "EjCfFiQEDiEEDUI/0bvRMjb9OCoQPrnzNwOAv6EP+lshTmdUhZSvJwgGUiGrNN8hNivR3hCff8jEOFCKl1QKG3OgOFYme2unhCdK1l/VLN2UmL" +
            "xOCQbtJ0zO9oWSndEJj80UE4h0l8Xw8RMFXf3N7HWeZPytFAONkloUn1Ac+cCauyhhT4lBk4h+rE3ecqDMFMPwJNVidbUskOIuHMnIvnhEQUpj" +
            "UsE9hSJWiik/NZgopTC6S6cQO4CkGFZu/AR4pnRDEy7usqf4aNvvT3pRKCnWRODCJ4TbdWP2gJhGQ/dgbFlBCIQMyZ6RY7Gnp+Jzmc8eWv/ETw" +
            "otP1nDGG8PMOjLr3YMu8sW47B6XU6vMlnmChSj0SmmiigZwKrIf+XSzXAKoyfcuBxkB7HW5hpEgmIfKGhZK+5DdgjF7C52OIuFHkTLUWK1ea4K" +
            "1oxHmSFdoJBWrWwoRBSEmWuy3kO8rpUQ4CqIE8SysiTEsN3XgjhC8BPde4h3+YigQbLTxxESuai6T5wKM7WSNSPAzAhq1eMD8Igw5jXCAAoEfJ" +
            "xtiMS9jhdNF/8P+YciDR6SS6QdzhH8ZVyukAJigZ11Ke0rJbmWTo4ppfpATtY05RM/JIIgcj67K+d8mdsC5VIdNEyDRJFmKd7h9wDhdHIC6cql" +
            "AknU8uucT7huSHmccUoN89lTrBmbrykRhCXHLozk0C39zktGcEhjVTdXEYQqnoDExNUrWyhrxN0uqzAwVUZiP5EHaSHAg4KEhCRqV7ZvPIXIhs" +
            "nFK0u7npCqONCymqCQM8q4EZ4oq2JD/QcFkpu3VnXPIZzVpDyQRhNnL27y+Nk8NrYmIZ99kmdLWd0+GK9DwDga+uLvkOYpQTTXntyglALh7JYq" +
            "vOmMDeNNVUsOK2HmrGYF0ztaVz0xcP0tRtxTKGPGpuuyUJYlTvktOa1DqTnF2OYsrB24cm1rRiUlBhTtkpNJZaLk1q25/5UljzAuZy1ZkOHC5J" +
            "YhTlaKPDBMvVuhoI0tFjtl6t2k24Uk9PuI8bexYT7MlqKItKc0Kyki5QQYNpQwU2w4peCkGPbfLxifUhp6yWcMJzvlNDbJ+iwwifXOXtL819qN" +
            "/y6USTNHFLIujquiHgXMlfr1PIjk3wC63bemuwAzpUUB91WSAsTpLpZWoFOcQskjGVM8Z4cTSkIThnTXj9Ig0kTOywWaGF/yMDVkmK6V1cyOl3" +
            "C8HJHjy8zoS6dzBlkzVuhuY8dlREJMTFdujWNXacCN0lyUyN3tlXV9qYfcm077FZVy7eGOKWXXg+QXzYpOTjqtJSNKmmi1gTY+m7jSBbl14gZq" +
            "WXAukD65H0NKXcQ8vtCkWlFcJcS3JcLsbBZcrn6o2Qe5nQlyiQCyMX2k+LeUnJsAcw4K2lk8j52CAuBJjJfxzioqpaolj3DTWcAEe69FBoqHad" +
            "HjTylBUpJZlk5XDNmJ0r4ps2c8pCCPd8LSAGQ3lJjjwr2jTMN7oRg7trm3pXmnO+rFKXqhJ0pzXekuXN4USLa0rpY9hBd6ovmBZBZIqx6LPNlz" +
            "7cZzFUqb4b3w3Jp9oOXKlZKbS9Pq6T2bfaNwPRQWwmDS6P99GSAl4i0ANTsvA+tK6bU5iRRFXygIcHUIY0S3yxKxpfEehZ3VIQ+uWtYtVNgEYj" +
            "9SGansLjibbxB1nyqanvQN4YskQSJoi8iVUd7AGHWVDeM9QY4HnCie+1Ks9t4sxGGc4dtZhEb2lJBbyuv3x1X2QImcD1rbX/NBlyjVVSEtFJZo" +
            "OQsYE5RQDhrFjn4SDiC5h4stCHVnE/OH5RrUyvvtbL6oK42MNguxsqRqY3T3+qqrNq0QOxbmE4Y1pS9TjbxKAyYJiFhwlpkvaGq9TOyEXiHeTe" +
            "1lYmsVCA5d0wnDoklptM0FyXKkfllX2/7cHHFHmmMi+m6dHPbrxJtbVjYPtT2DF6uAPYcoKxdOOTEBr0zNJ9y5Noeu3AZ0Tl+Ix34F48v/BW0b" +
            "kOvWB+J9HKdhrhQXmiMdYXKb4AdMd94BQyXT5IoVpVDutlXO/BtMbE0etV4/VY7TOF7h4DUNZRXSOYfKytKq2pGceosFCcNOO8Zd+58JA23nJj" +
            "oX4nxni/R3kS7aWTA9y9ByMfqAyQ0WzZ48YhwLlQWHwLOvLYP0veiwMMTVjGmF97owKMkutUUZlcN1jNEwdCUK9wKz+rGK4QKcNdDCMxfWezFQ" +
            "LB5df7sxc5IMT2yeE7TwDH+RU3qmNHpya8xovpiHZNX4RM2R/bSva3auh4aUGqg2f/08KoimXdAJqOSv5C7EJFgI4lK8zlNo+EIvu27xYBmheb" +
            "IIY2hdgrVzXNm99F0GhtM8YuxrTA4tQpqKxHWaFEWg21D76BSa1fET02h179srhUU5TiiHQzQ5lFZ/xOAxRqSvEPuN5ySVkr2iUQpfbdfuQX3V" +
            "TZQ5ueiGAGSEPnLwnCMjHZJvA0LQt8YNRBxbbVc1aRmfQL6Fyt1Z53zKNs9tk7MDxhkfRrnsa4wZ9s+TzW17sjFgUOZTd2d3DdPalJZPBYa832" +
            "AoxNF1ekF/wLSSJcJTrM+m8LxumVs+L/8MsdkvW86FISA0Tq9Z0DlczyNPhQpmzV14gEllLh3iaqvj5XVF1o1TdFPiYU9pdygtgxmOhz3HWzM2" +
            "po8cS1p7USpEgDEccNyNSItjqt+U6wUxeEcNYsdhiHXDLf+WEkrBwLFYPVCCoIhGJ4arL9qVvGZh5I4JSAtNr4HsuNZutvnGSQY2PcHAaSEe4y" +
            "aRIkDQi5WUqhmKp6usIu8GFS8Q+AUESnvpjyB5fqrGLqOfuEsMP8kHO9Q6mDsceZT2Te6tfByeDeZ8hUN/01e41yXrCUUsPMO4wlWuR6FdTzaM" +
            "zOUBSb/YdMDPA6ShLTdMOT7vSbymmZnS3U47zBZD7SHrej063rLamo3QQhLGvjhxGIAnTqT5jUB2Gb+6TO20FkqfFF9QRM31S5oJCwUU9y2ZM4" +
            "TxfrRTlPtnyJyKwQ/C38bwXj/44aXcLVPzYEnhbxolPOgXp/3NoBmeNfO/WP/FSrg/y5aCZLz+SmB9ypF4xdzcF8WKDiIukCjvjeUdBC9D8582" +
            "Hd4bf6XEW6BoUxoevvb0cFFItDPEw2E+vpwN/QYz3svTPdTkRqTqNMZhFCv1BHIdVC/iXnBy1ouY7EY5wHfQovRbvlvMv0FjA7unAKcxec2D7C" +
            "dTlqmLTufGa5FfUZzBzYUGV3JxiwAeuQnN5hJew0PzAyZ2DF9vQGjj5cqJLtLwjuh++/DEGRuIkspLrSTtPN3gXGT5QRJrR5Nry2nqCUms2RzR" +
            "Tq4UNsZ65EB5dDd0aj6CfrfX4qrppyYCcCZtGr4tZlQPm32PAZy0gw8Y5I2xglniAXcXWeB8U40tTeoz5ZLJG0456QTjsb1YO8GE1jCMu8Paet" +
            "6Xc/NVrl00M/lfFLvDBYP3a9A0ZeM9xrtcQegQQwGUwacuJsDQ8gENq77xuB75WeNkz1d74Zli6X61Yvp7k68YvDbUB5jyubnb96SC7+5+/ljQ" +
            "ktCdIcWBrQKZb9zPIH58WmHT7sa93vDfBdPJD78ENz9+kY2EvHAnvnkhXz/78gTpg0biYfOQMn+aJ00UcVOXiG+86zJPPhURL/OmBye0PBOE0G" +
            "5VWS3S/xcK/oJCiJumpj24EI+2cg8BSg9RSooBozuL475e+m1k949h2J/+FUoN6OU5T1/z8CptkIj78fUsmCB+KQPiMMg7sNYE/2VM3VDPT+v6" +
            "Sphr3pAX2lptqgNmWHRPmKuDlRib/h2mGEp1vb1u4qZg7zFtXTScJmqpt65O7wHMqS+ADzClKaLpo6oPz2XBX8sixrhnzDhVBpRv2C3uQGXn7P" +
            "8GaGmvpqGby22qzV4wRlstuycM0PKozLVd/orxiHbuPb9j2AXlpLsMhj2f89jJGMvvRH27952sbnROj083tzTtGlPBmOiWzugdpvZpnkiPT8cl" +
            "fexpaHOaFTO/6DrCQCLQm08NA7RrjVzTwYJphX1sjdyaLFTK3JCYIwhunt0p7+p5QCivlVC2EmTJ6nGgPJdLvD0tH5Rz49O9o+dy3yAmCzq9l+" +
            "ufLZncpVXdSk/Xqy5Y3ydjFlZ+QH9AmDOEf4fI/6Od3jffiEUQ4tuH9RQuDP7xcIpjhH2HKIL0T4DMCEWQcKZO+16Qf4DYWyQa+0/8QrMIvUbM" +
            "FnH2QZC3CDeXrGhAR6wP+F8hcNUFCHX6EdE+PPwC0XThv0YqWDc3W68R8yn2ujiNVMD3CFoK/94iTkdsdaEg7Et1niPO1bmN1IdTTJ/G2NaRb4" +
            "LQ61IE/gFhfufg7xF3QbTzInuHmD+87egBcabOR8RZpHr7HjFZpKzr/1CQR4QIM/sgSOuCXiDm9Ht9PFFBUIorQkk5Dwj1FArC0Y1wZ4i5FPlw" +
            "hnjInQXhd4izGJFG/S0ibP1ia5HZO590cda6ltHFv0MsFtnrYihFT4J0B18+mDSc4gmB7xD48hSoIxS/uCxiDk+h+MVLxGKR+BDsdBZm8SHYQU" +
            "f0T7cf5As4s0g/hRHX1u/8YhYk6c3BkXeeIVCPkQ1iFyMvEDtBFgT+uS7wtSAv1LlFzAUxPAiCZxaZc+cvLPIe8RTsC0IvAnOvteSLXyCeLHLY" +
            "HMTXiH3Kaek3vc9aT4LQmSDuXzr4to48VPZTxCRIgAfEWQa/EYtFtqVoLgJPujgsiL/IWnO+wG2YbevIQ8r5Y4SiTvuuOfgfv0gqS7ZWAAA=");
        register("replay_kia_sportage_2017","Kia","Sportage",2017,"2.0 Diesel","KIASPT","P0403,P2009,P2015,P0404",
            "H4sIAAAAAAAC/82c63LsJgyA//dZMhkuQoj3f7ECko3A4Ngb0ul0Jj2bTb4Y0F1inf1G82VT+jb5f/BN5ismW14Y/o6Rf7svcB6+U/2pUL9a92" +
            "Ud5Pf/ca78lDNWUSj+JcVb/+00BU9KXVFPSWZOMflF+EuK+44XilusKG/78lnc430ZKWV38S1luiKm+HJ4zvjf7csbSn5imMuLULoV0WJfgvsr" +
            "CjZ5sQl+1iOfHKzPqFKcfpb0kGJ62X1EARNTEdGlNg4rMn9JsSn2On2h4IriFMXSrX35kILj7uJKXhrFE+DKSsmz0KcUXFE6+3KzIkOj1HUU/4" +
            "ICbyngzVVelJXq/VFnpfSzhONvbaWY8YyWvtHd2F1NgV9RcEX5YF8mHnYpLzMKbKGsn2WhjYCpvL+ZMsruM8oYvyjKT17NPYqCrhQyi90NJl49" +
            "yUGx47N0PmA7ZRp5HJRwsbv2LymXFdGo0+6BHl2sFI06DR9QzD2FPrW7+OCMbnzjC8o0IsNHHhY2UzxRWMV1M3+0eJbbnAQ+o2Q1xtF6P6H0Fn" +
            "OgXGOG+IQyi4IeUWZR0NIfLVe0hXI5I7jzsJ3swo0n2UdZ+yP7UANOiqUfPOwd5Yw83lB6u8v2Xijx6hs7K/WIkre1UkL5nnf1RTD1VzOs/LQV" +
            "pg35EEJ+ZU8QnwxvzQ6QeiLxBI6tCPmqCPmtmGXUHtFr/LLBQ1yAjNan9I4D387dcEKsv0r+G/PvGmgcdPmVU88TztQib4M73Ur+Vn0gMhp0CA" +
            "FtBJHLjs4rkD/VPPjqAXlpjtefIGgQCij/jo9GZ5NbQLA4fJSlFRDGKuLZ/pRXerOjycEH3oLob0D1+bJSRas2uxx/CEFObQRlffQN5KrNDMQK" +
            "kn+HfgLBR6CEAfqVKbmGFnER2yo2iVmWICupMQ2THIll3YiB+rQsQkbXI9gvIQsN4TclhcEviODF5yww7gOMVwLdYVLd7xB82+KUTkog75Xl0B" +
            "QO049FMSb/0fIq5n2AG0w+NOyj/WeYGOE477G00Usyl2nRzBe1CWOjC7HbHNPEmF21WDHDfy12goOn4Aw1KPdNo3n+DIMrK1+e+XBfri7ltBcZ" +
            "mddlO5vaODawKTwksD6PrfQY6s+dEngFwXf8EAQUSR2YK2JxeGarN5rfD0DaODcN3QdCyFZSh93ejSKUxMWX95GCtqmmObAYkNYg9zHI8dcOhO" +
            "LAqhAYDjmzNU3USFUyKPjeOY9RB4ocmeouggqDbjjZkOFrjqkGPx9aJ0awCqfKn43AjjAH7r6p60YO5oiz3x+a6ZnjF+RIRWVqf0KOILvgbgsm" +
            "G7bTXSQ+utO0xrrNfiZAeInJNAhcb4bKJkbO9Xh77kCuPrGYxeegrGQuRaPySae8RiKV2zqRI6kYTUR6GwmDD91uh3OTUCJD1jJOSSjFGah4RA" +
            "DoMqhNIFkagySWAhVLHUkClUxFSXWwnNZMMIdygDIf5GwTxzsM0Khja0xswcvFlY0xECpXhpwmioPeTcmbDaASb3Hzh4alKi8SLlTrQpVaNl+t" +
            "KYzd402YmRVDsWLV6LOoZJkqAmZU9IIlodYZD/X250i+xavI4zzgDGHHlHNEL6Ti1fxzaTysiomOl+JEjuv2sGvmELGFL/s4OSQG2ykWXDTiSJ" +
            "vK6qMYaPbgZ5JSNsjms0h/AfIrjxGrbvmJxzA5S6CyBXjHOTxG+WOxKyvccuAJR9alHEaOalyvp4uUGdkW8inl7wXoH2gbyCWX+rNvoKhBXOdJ" +
            "UnUybMbvnPw+UHPzEkuzYeXQhLhUxWZeO1XKIXC3RYqTdOVFDPRDjq6vTjnBT/xFzP4iWmVbh8fBS9TqzWx/quOZgeA9KGdUrg/vwC28RjnLyI" +
            "u5bpANGPqAfBcntrIk5yBi0OAHofapS3s7EGn78RrUigI9iLP5vi55GLQaYqeQOqH25xahNN6OOlAVomS6IEjVAfeRKGd9/bGdGnsUAg/BrqVJ" +
            "oMni6iPFbK1xPwi0RyMlSeLREkioiN82dQWCAFmb/RTUp4gvQYOyKRCnrgZY0q0t9frm9Gch/sWFoE6CIh9f9vA+/gAKH4JM6uIHUM4xkKoGSm" +
            "kjMvHwaqDsyC5QdLZ3Rm1pEXWqwKUNgtStTZNSokXmSvF3JDiD2Z6EXFWRxOowt6CCCA9pQZLemJZJIjdV3BmoVVJegDBH3Nkzay1xZlE45Wo5" +
            "tHxaFQf3UBIFc03uJukUhwCRdZRl6MQEXZkeMX2O+BLTlDVOkrvIFg1t7TFa0oEsomrZ6U7bCxB9QcjZGt127CrB11zIAdZgJn9NxWI02dnFSc" +
            "HHhSwn3dCUpB64mMcpnnWt4KnGN8b+yC85Zp7UI2cMbAeFc+R4WT4BEa6NH62jJI9TlZ0H8DBVt2Fte5w2m/seE1qdGx32GBjLyqB1y9YDyu6B" +
            "TFfo9vmwksI49xEm2+d8KHqXI6jCWdR5WfXiWEH5awxDxpn/E5sBQ4D2S1AL0I72KqoGR0TOgbPs+m5pqto1cmQW4Wwp1I3kQ3rAiWcodMeRRo" +
            "BrWx1KW1s3/Nwl8NCdm8DG62iOqQ7kNhD6LK4LFYu6EWDFlKVZUJ1BWd/iX4AmzX4W61g33ofQxPosM4EuMy0o7j3F6wmyoMNyMVMYlQE6LQcW" +
            "E5dNRxpbtMsaSkJdrdKjELtAgxES/z7NN2t0h3XXfA78SwKFpwujFA7T6ljF4CNOTsrMMdLn2sJiDTclSqQmP86wNHmqOqZBJpxnthnUAmBMeu" +
            "xJOtn2iD3L8NkJKlofjBTrLqAAPCtqVXDv/TFnWeKTBcgOOmZ1NcZWP584SaBaqD39T7Zs1tnQ6UaAfm7pESaW6rSeOojnqiR+EKPI8bTndpSD" +
            "PgiCrxD4ws9mjs4OuzouXwtwwUo0HbyKObweDRsxljXmHHZLZVyFz8pyc3CJkZ44vsWQd6nzPGEchOVpO8czXWLHRAadeDBrt3GwpPnaM+PFE+" +
            "qaKVqJgFsoINkOOnJdELQNNG9D2vrYWQYqNfDQhnaEQ5cEuA2w0i3iIRqssdupFPR/060/0tE/0K1fYLbpFrlhxJb+SxW9qFagjzTi71T0M826" +
            "zP3iWEf+SEG55SjBvFF3AmTmEnlrs4MtgwKnbpWGc17WvKXxO45vRYmgb11wGBR4LFE4prUQ0CAtOJ1S8AF5hIeYllWuMWUKVA9hwTiNfB3ZJW" +
            "mIsGYEVMGTQdXl3YNJnvpCrVPOOF1yXC5zgK+LV1Otf8eB+diU1BDZguWvkFqVNuu69Q7stdKiHkiuD0m29AZklC28gCRVlnPXGcpNTUsqgWLG" +
            "WAkDT7OLHDo1RXpbQ3rO0QnBOK3rRKJ9Mxx5I7xeWcvhbDA+dAZxG8i3mRfReCN7XTbeQgVl2fa2K7PlPMzbrh7VQGN97CWoiZEjNSwgcu25TJ" +
            "v9g4vKloViD6K7bva12cda6zhfz5JHXmWVU46bzsz9wFF30cZ1ccvg2CDxKbwnx7qiGuEMkqfvB7nRaxg1ZO2itiDnTsM1dmmcI2Wnzzhmxak7" +
            "Tdreq5OPIRsBO+V0uiF1Em7YXQVoE0bdVxox8oZoGAtZCLw7phbsrJqc2sRRUwKjonqjp1v5BbJBPTikuimBFhyjrku/x+AUw8VTwRxmQ43Wu9" +
            "jLshqKT/p2JbfR5PrCoaVxzVHW5zmnCxZgcBlWTqtLvaIKxE+j4bdhWjPlUp7n9lGXwbFgA69JZXDeDk4HVOaVPqaYkQJCqe+kI3+qzkF1rCCL" +
            "zkI/wagKtsy0htAZ5u2goGcbYRauHuZLOu88UyZJkzaDmzBT0YmXKqZnh+y5gSbWVGtWJNtf6NnJYbfVp+1lxy3PRAPWvqhaVsCYeidxYoJVPW" +
            "VxEp6mZnnGMS84dhbOj1EG6XVJbIg0TVLCop8H7zlr6XG6z2R5QDTwzRURH0NPwpVfcowa86ZLn5s5xz77NkzmERdKmnTZx3GQQVjdRrnzQz9w" +
            "4AXnUeN0iAqr3WVvLMmOylFuGqdD+aiWqqjLlOeTsWPD85ec+YyEBD2SclctVQPR1oRk1C7rJlPsTl0GBDl0ecKBJ5waZejbZWPNUHUrbD/4Vy" +
            "sJbFM9e6CzYAPbODZCcF0w18IVj+rynXx+DaFXoDPMoNGZ6pbXbzidC5Q29zGrVUtU/Go08uVamPd+AYqcKks8x63eEOgVCH8AjTl3+EpE1Ol7" +
            "u7rJo3BtCrFwUPRdkpPYagCbOJcwvomQV0NaUvQ1aI87mLZzX9nthz472cXpgh+jL73UffZKw7R7VzWSS3oi5SidBL7k4IrjeQRE4gTXVGzwO1" +
            "5dnnHmEvJSWvW8phh4jVmnFb4r1rF5kU81KPX72ArqeVUU0C2CqF9yWurPynWsKpYROK9CcNeqRxERex11qixvPseYKUZu4QSJN9mIeV2p8YEW" +
            "9/yPC1ykrjeGpGtQJwj3gS59lEWQwBOZjqE5VEiuO615YeS3GO0sjj6erqwBVzSOmKV55VCuZdm50/Faet5x9CdOdBzHxajACeXBabuM5SMHzJ" +
            "QDQzumSozpQufWEbxw2izlK84wYTO59H0cV/XCHLNkexj0QK/fhrnUjWA+cFhnrJyVxIINsyrLh2TtNfHiSbju80Fec5rDIT3mxSFLGVNTHHXJ" +
            "zboA3QiKUlKrQPLBL0eXKdXWq/54kAtIdb2eg0pHGY2eLNYppelH/HxLmY4gvLUJdnFiqVTodSFOP/xCPl6LvIqd4+wy60hBo3uvLzEtfuox8u" +
            "EXGCeRfGmhoLcLTqcV1HUHOLG4w/jp3YRbTInjrU/iR92gFIOOcq3m6KR5ddHX32L+Bb82pmyYWQAA");
        register("replay_citroen_berlingo_2014","Citroën","Berlingo",2014,"1.6 Diesel","BERLNG","P0113,P0102,P2562",
            "H4sIAAAAAAAC/9Wc23bkqA5A3+dbsmqBAEn8/48NRtgWV+PqnJ51XtJdqWQHdJcKjPDx5scifEz6J+QXln1+RR8yPxSP/5uPgR9CCvkNn38sfc" +
            "WfcLz9D25T/A+jgw9oChdK+n/UlPRD0chfhA+TXoqH6D9uBHH2wzcEZSlojlfxg0GvpIfgewg5CJ8wgqQdHHB0RQzHCyJ7SpBvSviJGFwtlJNi" +
            "ZAnuliwRHS/Sz8MP20skc4QPH7vHII5cS8QPGSibMHkD9oP2h+GWiCFTL8TfYs1mEspCktiiyww8fkgMRhYyZRR5hLIOO0WgM36smB5hjfG3Xi" +
            "6IS2+gFVs4Ke4yNBBrDUW9eWeU5ZMsgvwtkrDCgJiglglY2VGPAett/qvdnhYYZz6BbgwlgTmfdz2liJY5/6kY8m9CeoW3rVByCEjCwxEmcB0O" +
            "UgCwjrLROPeh+OsY/5NWSXVsgjM2uWwFFcUnMz8o4gu3G1objKv0dGHA5kViMR75oUAKE1YY/xqTHOkwmqETWLHsgGVPBwXQ3tpWlF4yl0+Lti" +
            "+KzfLlW75izRMKfkFhSGZIE7kcOw1eOaWLdkghgDim+Ji/E5yiBDBKujsUJ6Fni4JguKaYCSWnMfJKRzcloFO/ryklJwanNB1c1i7AJ7ibEhYU" +
            "sbYLQgsIkWlSAMwhjsSr4sfZG+J+AhV361aSrIjb/TiDinJb7pwS66UkBXEIQwZ5sOOsOGekX8CbkYoNz6H25iteVjLxh2szG1WxKAh5O3YgJ3" +
            "VCLZLibfV2RhR4TUlCYdekxssNxX/DWbPknM94Z6OLgj/RWjMJcpVYOFOiySEhrcgdRvbbFJA3BqEStQOJ9UVRtKQ0RekL3CskSLx1qjJlHFam" +
            "8yKZtGiXDGuO2LQDyVqOTmk5Xk6InKLwOJc5TZEA530WdxJ6BF3fLihBC2VNIUaaFC4NJdfdkh5TNWL9TUnVh0m/M4xNZY2VXLxjFeHuJD+nJB" +
            "GCthVaUdDYcfoYUaINym7vHVFM5QluRNtDRqlQUEXqXbU7Ck1VeEJCnTzCEZ7cHuMqUHcZSTrJbsd1ZSn8S3bP6mFJHSJX3YJMA/YZ4bwSiZGy" +
            "tlTKKwq8pqQNhRI9Okpphy5KToYSgkpyVw0v+Im9QQkrXnuzWEqh0IKCX1AwmljJRfch1HoQhMla2hb+booqP8T8Q05iQvoVyzoL9YOAcRZaU8" +
            "gYrIueJBfqKaIjcEZF/nBp2vskRd/oiFRLVKV41E38FqQ4kX+GMEasQz8PIWWeIOVAn5t9SiCxkW2mWKgL7UNdJB1vodA9lZhTUK9FNDSjEPq+" +
            "2phSgmS+Lt72zRAO5YJixhwHGeRoxK2zXS1XmYsv5cYh3ii6aFz6zzC69zXoYeLU0idpDACT6n13+vmz1j9N5tBCFErp51cU/5qSfOpoQ2mR0L" +
            "waEYJTI0I1uukHc1fcZW292ZFSlct3/XNRVuO9NxQ6iv9hKWa5Tmo5B1ivKPdMDMk3zeZFId0+524vGrr7kAsCK0jYhvgjScehmsWL1Zwkgpq+" +
            "RrpiVKrCTSXZcDW9Li+wQGQFVYyK95RkQZEVllnpEyW5/HDMV1NydAGQebJkkWgXFF+PxC6KVLl6CMX3kGRKKeMe0g14HEOs5eT8vBhcjijpe5" +
            "XZYgAcD8ir6WexfVazAD3J7Wyf764q6JGyjLH8XfsvKbhNuQd8IRispaJqjmpELvNcOyxR+ejxw4gSa4gEPByUqCMIvIUc+3E8cWbJ0OhuZ2YR" +
            "pziiLnNjnHz4wB0jlgAnAWHF4NcMHw10YwkqAcF1nxt4GojkyKo+mK7eplLjxuazFLDGD7KqW2F8paA1BiFFsTAqxHyj5lyeigAaSjJ+6/y4nA" +
            "v1WBryR0w86BLdAmLLhxDm1tGU4h1gXbXjnBKDHZRhCZLKhK6R12VYgZQEb3kwaPErSpkDs/4kEcOQgiHErtZ4pNSjIzeY4J5rcRJDrsn2MbEh" +
            "Zbp6LmcRJ5BqVC8aMsynO8Q1xb+mOF1vw9WdkYq314ZkvgFqYhNG4/GWwlosZTzOOJBLGAzZ8RvK/Rkr1JRSbKN2omjU5863nucQ6zVFvqa2ct" +
            "zMdBT/DYXIjC23puRpHxgfB234EWZCP64Uc+n6KjCgO181U5tCSDcya8hwUNJDJMOnaKaEojo8iM0sbNyzlihn43gINad4PScRV5hSuikuLChc" +
            "PiKtI276rw+ubqlOH2qme9JHxGGh0HdUfC8l6jHJ8TZLA/JLlG5c2Z0POH2o+jRFvDmWTr6TyujExEgqNreIqFpEu4DAbSxZs2fZkl6QxJWMV2" +
            "2mIzCVkq8GZMiIDwyJB8GvGP41I77eC3R72WUgc2Wu3V6CZrg9mS4Zm3qBP2fcrUuRx8ZeWnloRvgPGQFXe4EHBmzo5X/HWNlpZtCzvzzaacWw" +
            "/1eMzk6/kIdm5JKIFYN4by/mPWPutzIWqhnxte/HbxnmFxitbiuGfc2IXzI6+3jFgKFMr7ezjZnXunUt46rdd+XxB4zVXghf+ovYaS0P/Cqu7z" +
            "CebKxiII3XEeqmfs3Alwy/z2jjBywYBK/3cnwfYCOevmKYPd3inzPanF0xJrY+ZxR/gWffb3O2tvVvGW1uAK8Z5kEvLaP4nN+oP1rdfsOY5uwB" +
            "YzfPtT6nGRi/qnF3GCsb6xg6z+GC0eQXwInPbTGKTHcYrW5hwcDxXro818qjYvBfYPQy7RlhT6ZfrOMxBuGGfUB99rGtYbYYU98v8YMmOXuTEV" +
            "vGTB7T+PENA4b1WM3ALRtr6/Wddaxi0NeM1sZoEtcVI0Qovw0bDLvHgIVM6SkGTfQSN3Tb2HqXK+OGnU4Zg3XMGIt82zNgz/fbPPe3GK3P4YKx" +
            "qZfO1uNzbniqk519tvWV7/8GA1rGY27YWQfv2Zj/gtHqpbHTd/J4yeCd2uEFYzHL6Rn2YR0w7G9r3dIWY6nbGePSy3jG5tyk9+D9nF0z4DUjbD" +
            "Lu3DDus2vGe/voGDp+vLExN4lj/MJfXjHgmTGTx4IBf5PRxqCmdnDhP2LAF4xpfim+Hzb89ikmf8Mwv8uIHQP3fB8a+3gl0z/Qy1Ms/EYeq3XM" +
            "4seCkeWBb2LQxOd2GI08rngK5eiJOQ+wHp8Di0CIPuDqS7vHPVy4DhDmv5jPQ523tgqG89ssn7KnTIOgjxwvMCHo1Txg0FTHGfPAboSRuzgcBc" +
            "MfVreruTqNjadsCibKd87bjvmryOYAVOeoFxgsN91A32ibYQLEOJbNAANGXnH4gDrIgCvZkAQm78tJeTqODdiMifZD9sbEFabcufX+Pqx7nlTv" +
            "MeF48ACf29nFpIWC1pQ6PhM6s7n3lBSVRSNHZhrR8CFhuc7bY8qZCH8ejcpf5ZYquw/SGnPo277H2OtqTqjN5vwO3JuyUc5Qsvn4UOnboYm3wW" +
            "gMlds2shq57VUOdDYY/jUMOTpPwLSYSjb5bTByFKloCm6zYUumNhu1mg5T4nGKekHNS0eYLmrtYNQhmMaGUU470SkazHvK+k7R2MafaO7FpIK3" +
            "u7VRWV85ci5/i6TuT2hvbwyuMGDKcastjDeh/TxrhBEvZTn7WvICqqDlA7fHjIpsJIbjeSMZc+yzymzuSyQLTPU4FDkQPqWYGF17wWBK4TBKLn" +
            "gcSEdsj+MLhY16SgxA2RL24dzm5GW6U08lZpVjnEbFLJBXv4QpAVR5Ah4/R3U7c9lwuI7OFhu2ci6lhHM/DOcNJohflkWxYOzZ/XN8wJjXmCOC" +
            "euCVvq9DlT7fAXcDq6FjmGSoFs1J8ajvQeUXk+SCv4Xp7wdfbmn1LeOSFOQoexOGD7fEYNpnLhV16+uNRd0gqb5Yjc7ecwyq26NPGAPJklo9WY" +
            "2xBZO/SnzpawkfjG0P6AummLC9r/yDlcu5vZ7mlGI0O5TRpeUBRE6LlqmexGAdaYAttGc9SkZw5bE1+rEM8snL4a9c3aKYY1gEUw6aiqynGEqx" +
            "ZmzAIwxLWVPCp865pcAd+Ha5+Wb1kxnkZleJe7yQzYVxlB3kOnqaVyO2U1bz+xjiMJFN0BegQUollnv7JVvy7ZbE5Q5hHyNIPxVBBMXeDVbzm5" +
            "hgx0nXS+6+LqNKHcuqjsX7PDmlRMojSuk3wvmwlyOwM9OgkAgDCn5BiS6eT52ApqHrr7Seh9vLjpRgMAK2NweKX1aXGLISAKT5aHLuEmP2KRwM" +
            "tp8dVhB1ozZ1c1aFzjvHMUOdVa4ADCr+nmKRG4Yl3doNSKml4w7kiO5xHjjPq1al0Bv3POSYOmPRkKCuZAS5jIj5+lN1oHwGOR/ZF0oXl0cRsr" +
            "COwhRte9/lkSJ2qy52UHRmnE6uC6hqLBJRVTKKYgHT3wkjTDXPkMVAOS0i7c4aw9uYqDrK41aeXWhJ3xmOqCoZdQEhlVVkJ3sqhqQmRmAk1km+" +
            "XmP4NYaOwZ/vJvqCKbvx5r5VkZzAjDHBUKi84MJYeXDKNb/KvmRVnNKK6jC32bzA9CXnSYnlQUDnHaAsILlELcM0re65hM9kZVQxUx4yuKPvuj" +
            "zbovS+DWe8E4E5VjfiojyOT3xbPR2K3T38anZUxEuqv+CY15KaQ6p3ZCND+8CSginuFPTVoglmdInmCjRVBSLJ2qotwX1VtisdmrvmQV97pzJ/" +
            "zW0vqcdSPlG8vrGO8uzFjtLX0OfFrX8BaH9UwKRWAAA=");
        register("replay_peugeot_2008_2022","Peugeot","2008",2022,"1.5 Diesel","P2008X","P0562",
            "H4sIAAAAAAAC/+2cXZIjqQ5G32ctDgcgIcT+NzaAIBH5U11pKztibtyXmnG7fMog6ZMgER7f2b18xrcr/8E3uleK8sK/A5WfXF+4t4MXZnLz18" +
            "rPEF4E5V/+8fQUBdsHfXhzKpTYKf6FFPEBinedkt+sIbH8BA7tVX6DLxSYIzKhQKT0TprCY0TQ/j2H4+zaUHY28i/ADO2rG1PyzkaaAu2D+U3x" +
            "5V2YkBy4/bIthM8sFN+h/CRs0xncO4dCwU6JVhQkXGeFOiWt3mJH8TE1SvktVN6CL0SidUQmlDD8v8wX7QKAE52YKNhAErrcfnkbDw5n4UMoJo" +
            "oT4pMlpHhcLJAlDq/0yQTSwtCpOXFT4wBwtbEJxA/rBBEEUgro0Su9dTTVKSW3eK3LJpipCV4sRHpIMF6wCqFgRIm+fMZvlCJ4uJNKe0pPZyK4" +
            "WuY4sBqRDUXJXOipS1NSc5KiRfAEJOfV68Z4EGU8MgRR5eQbsvBjxeSOoVdk5qZgh+9yAwNVAMuY8IzTg+NjTt5xck8k1SNIOMNOtA0rxbzTKS" +
            "NM6piuu7kbqiLBt1chvSEuOcCGUv4/xPMpjlFMlfsUN5pLlhxw71iFhre5Kc5IrOLSkDOmJ8DRVhBbPqjVRvlscNN1HDt4wxOcvCZ+1iXrYi57" +
            "TIwBl/jcUj86rRUkqmaDQa/nxnu1PumzZ4yZJXQQSw0BpFq3tlTA4rtqfWIBOea5UeTBDlJ/ycmMltHhqsXXlJ3a1LAgpwu0KcWA7NaK5hOKBw" +
            "e0YvJio+C8JSY1/42hYaCk0qxMXZXHRX8eBl9y5qK2WSo4mOb2SFmvAVklGBsOJPSLybdIkC89hiWWIwKV77Yq1gwTHfjzuHRfUbymDMWKzVIY" +
            "9Fp90/Oy+iKX1yGZUNwIhpDXArS+8BLeoxpWZrLBJAz9++7nd6fl1WeCZDojCtV1wJTyWg4DntvahuKXek3UMzTFhBorUyTKurTIx/m69BvItg" +
            "YLpCtQmTqipVTjudC2wRwWuNuQRkLgLldgiGGny2FVGyFgWLfyjDhz3b7UamIpVkvuMBdiP1BC872ZWtocyxcYOjO1nOvODdtj6txgm5u2ohtK" +
            "LhjoqteLkbkraITJuDn7zHMNE9e0Uv9UZH++RfkFZQupUptA8MfluwWGZ+5WhqJWWkJP6DI1K4fxfOP1S87cB1vTXCslQPQm1L+2lABGGI+l6s" +
            "PT2AQdU6LAVhzqMipLOLXrWVzTwXkt8SVnq9h2Cbx9MOoEs9XEYIYp0cG9Xgu7Ufl1ydFW8hnUPuw2KrLjlBrJTcLlsIwwsc0gMWpdn7ZKmccm" +
            "qCUlwGmSkfBMHs/GZIYp8VBqYjwbFHhd4cioIDZnKZoRwJAzt6RS3RCKv5zj31PWCn14nySZEECtgrat5liKHBd6kWND2W+OLbveEi+9bhuPWU" +
            "hVXCYYSHwlW700QRVRUZTTCDPmZlE/jOc17ZecuYOO686CPPnMZ35TbZXIFhPWkmsfC/XtQHobST1cMMKcqF+6zg1GGKBRKlcrqnDgGKl7jimF" +
            "T3eSxG8wt2golWfbSpo1jhGmlO1lvuiPStH1glv2RXoTvbIthYrTFOc4fTQ8nAZNKdUwiVpSLymfgjJT3emNZbboAc7V1NS4ZJGJ2J5jbYNKVh" +
            "RPiOcbC37RLHmgGufqzgSiF/NQZZ6vdwSW3bUUx2KuRvl8yAzYn3hqE/krCOYnIfst2Z8g0f1NSH4a8us5yaYTG/Wi+w9+YgGJxU/4W+uYQP6+" +
            "s5m4/X8gdvZnrdJPkPAgJEb/AOQjt98fn3keEv63IP6XfvI4BNODkMOZyfTTcNyDkOsA/AbC/4fcSqPID0Ku06gJ5EYJei0FJpA7sWMCOTn5fL" +
            "/IuQNBsijLb0Dg0k/yk5Bi+19DLtXeAnLmJ/Co29+Xgm8g+ZOFpAnk8rz/HY/9GJJ/Y+Izj70B8b9y+/xpafFbyI/WcU9CTjx2gcRfTKwN5FIK" +
            "HoNki+HcgPgryBKAfw/SPdYecuFsfyxyvoPcsI4J5DAn6Yfh/Dp2PoFE4J83pswhy0JSDwfiQZS+h3ziJzaQS7W/4ycmkDtScB/ymBRYQEyGcy" +
            "kFf6oKokVp8WkabRDUzTFBfokFmcvf1f2V9cQbF86+qwAtONQ5ST+QC9LCwEnOsTh8R66PLgXkgyGImP3hQU0DEalOpiBPc7i3VTn/hmQJGqqV" +
            "6ynpeqzpCdBmNGD1fDlIPxgjatB2rB9fHFI+7BWZcLhzlsdz/YRDlv7AYsCoKGxEKRPF4dBGhLonbzy5ZEMOyymSHhnbCeLCSS7Q4j5mnHFYMc" +
            "T13NAPnGzJ8e58fsYj+HGOzrcnxNJWXf40KuVgMw65fGygOHqPDaVPoJQkiWuT5NKFcdoVYoGhFTNODmE7RNc+WVQS/erJNpjMSOfqlVY3lp6m" +
            "NqcU3imaUWDOjHOJzzGjie1DzgyGHafOX5RH+FTiJKmWkPKRUAoGeACzhcLSTiwYEj3eWwqsMAF8zIcD6Ee/Sf0ICBtihqn8PHgBjHzo5LXAjM" +
            "kJTntgam+zFAKlJMGoWgX45HC+Daa2WUn5pbute9+C9Dj3Kd4oyYhS2yKPO9cyph0HLTnyKmFtEdRzEwDy8SyTCWdE53pkp3cdSXNoKaY9Lu2Y" +
            "Vhzval/3eXySPrzGvXlGB5ZuAjfh5KpHlA5dr6LJcW1uN+GMyzhEh8tHWuXey1s5Leov6uQvOVvew55s5MSkb186i704vgOrfh4y4yTnYC284i" +
            "pgrFOWrEC7gNlQtsRHL4B+TNeWMnWnY0ZbaG6tHcvEhFl01XeWE+hkgtm+jbZSPz+ZZHlbHKqeWNzGZEUp73haWw4GBvSVQ1JkE4AlpqfgmfRi" +
            "dn5p6viWsrN2t5JTRW2UvYryPujupHpkGGht4zGhbCNaL9iR4i5J20xKct5blfs2mMMlI5tg7VauFhT5wiR3gO1VuFiJwZ2L8FeUuS2Eskfle5" +
            "NMW0RLKbCvtmp4JgrnB3dvcnzAw6JVOPpykNBPfMmwuttsi9ZU33F0fhvCPY7jsjiF07tpdinhLmdI31mPJ6Ken6DavxyVSAlPcqjNz7BX36+S" +
            "u9IoV3PM57jZjhNzsTtcevN6KwLJ4SwjTA8Kba0Uu5foc/4GmNl2qm+wKB9pX1laaffOk5e6xJazSXLUHZFjG1A0ua9lNuchMw67HNYGuXBWm4" +
            "yVlQUlJdTCkzbhSaH2AT5HgbU5Kc/7+k7yL+FOk/MHlMNVGtNppIcljz5yUAlYimuvQ8oznH+bWxyqV5mc7pxg0D5jhHG9ye6qbPMR8XCvlgmH" +
            "z/2mrVNEqsaoourGtcEkx7vsuT1T0RdQjNTlgyqwrTgws3mCTOcYcvqxw20M7zDDddo1mNItzdybPufVbvUeh2CP2XLDuCOR1X1CKBeedjFWc2" +
            "yFCctlgHK7jHacoFYxKD33dyj/Au4TGdf0VwAA");
        register("replay_healthy_unattributed","","Unattributed healthy reference",0,"Diesel","HLTHY1","",
            "H4sIAAAAAAAC/+3cUXbjKgwA0P+3lpwcJIQQ+9/YwyDHkJBpbSsJnc5PZtImt24AgQQuxqu4S/JX5y5AV3QXcWl54paHgPpfvARP8erLi6g8Bq" +
            "nf/g/NiKAE2RDgfhfBbjYC/hFTEXyNLyDQlCBTAoWuaToikCFR+0X8RYSH0MdOA4LnImq/iE9GqgERJiOYPC4fmylBdkQOReHsVaTyFVlXStmL" +
            "tzfm/68rHrj4wNANEuIfawC5wF3nMEG29ZIFQrh0kLNXwo8IbcvYGLfPVQS2jpQf4UMGi3R91cK4G3XnjMSl/0lpCyzZBUQuSIArpUtcB6HkV1" +
            "H+kbD+rLZp9jApf2e7YjuFTZXymrNI/vryldqR8kDK3RpF4qawqEIXcOJ9adCHRvqaYWWCGZM8SNf9J2PYlkmw5GMQRdsbjjGITbfJc/ZiipNB" +
            "t+FL9FKSwEGg+4CCQTuvqcITKlCHxy26Ly9yzu1UND5URErEidLGB53dSrhMcDeluY8pCNH3/cVGYVOllkdsL6bmuuJbRWdLgKVXhbu4uVdZQs" +
            "NMSvQEZeIyVugVSjinMPZMeRJpY0QzROALgEtuXSv3H8y3GWfGUMhrNrZXcEalW7GG+mS3gr4M+1sA52XdGwY9Jo/qGOtkPqguvVt5SPZMkC3Z" +
            "M0E02TNBHpO9HcgaAFMzAwjVBVn5TW/pY56lJYnOjKEPCx9QMHnttpbKra/YKDXoBjiFSB3NtVqGdTQnF7WCFmFTqKywwvhz+ZpJW8w1YnygoE" +
            "sGW4anZLS5WYM3nFFu831R0G1KkyIhOII+J6a9ChspzRreEsHpkBy+uribh4lo3P0+AnWiq6nveincKBFvgzphXqilYY71diWGHL/IXiFTRdvZ" +
            "BOk6S/BNO0MTdkN+Z1wPDjxN90YKNVHXQok+rL/QdAhbIrFdiHH5rgkioYkIXZFiFgQcA+jkYanQjEqCdq1ccxUR2c3oO2sBM9aciNqraYoUEX" +
            "KvTKPqzSeYxzKFEcO2TDuqjyuxtjevwXuZOFOqDF99XkXFW9wVItwCVFf2/qEKkNRVqDnDtgxC6TN1r8aKKZsM6By+jmEzhp1ugf0ORhsaVYFj" +
            "So0RaVWkrMzSptx26pZg7nOYD6NKztuVh+0fE+TIp/Ic0WK+kUJaO1ymcI9y6BfSFKlGmhSaS4lNfZeJ4jgxebti8gs93yo0QXBQIt6viOalTi" +
            "sepc3rleWBnHKauq6qYv5svTwJmF8zuggHNGPCcqg6/hqGUm1B0vVvPMY81qbQ+dgo4ViF68VKJIR+VWWC0KQI6z9aYcKdSJR6vAK3XT50tfys" +
            "StpGdQjCW0LRLRHfzzR5salCpgrW+fCkcrcVW/pLapA1gOc8SVxIulmC+5CmSmyCgF8mGfhVijdU6mTg68knVWS0+4PDkvXXiLdB2s1/U8VNqO" +
            "hxNC1Z1A26VG/Z2sNoOL71lxIWqF5NuobcMn4LC+LoyQHxtyvPtwRmUtYtgXNK12FKcnSL3AU5MKRHSpN4zqTQ8rpkr/DsSpmCEdA33QVNlWCk" +
            "NBtRfzeCrt0/LSnCASU/pvuogKmE7sSlezcH2vxSzxjm9ScZnJXB+3YyYryjhomzMricu4gvYMiYcS9hoDDyixjgFMZHVk8ybMt47ke4CVP2Vt" +
            "ED/ATGiYNxLD7J0JyM9Iuag4zUHTZl6gFKPTKqDG03kAXPfnw30PcZMmNQbxs2Z9iW4fACppbGYgRLRq4Quo3RyZi8ah/v7Z9kyJaJrh57MGZK" +
            "nbHeODM9AzkShXkZtGVCW+7RqtERRtd+K1P7TWyYsFWNHIbUny90uxmyY4RDf7eHEcNzMt09iLXBOe1mkPR+qe0cP5Irz3JWFXkrTCxzeE61xt" +
            "PdB5gYyT35GyOnFDRVfLvSOooMB3fdcK7KiRjRMc3gNmIeB/dcjDNm7gb38atp6kjlCCNSnb7zu65JboVhoLIji+OD6zscZ+YM8t6/3WG9cy7o" +
            "Zos/6Ag3MQedXo9vnbWAmPIbffTjHYHPOOgojmeZ2ZzYVjysnBzeFyfUPwSwyxn3H3XK0ab1JPiRfvhyhzCNd+xmc8g32dUJ54/tXp3z/efxeo" +
            "ycx/WXlUOTOqO13BGn7z91az3Uu17B8RXSoX74J2fZybNynsf52Zw+zls5d3F+h/M/HcRnC4RXAAA=");
    }
    private RecordedVehicleReplay() { }
}
