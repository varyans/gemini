package it.at7.gemini.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.at7.gemini.UnitTestBase;
import it.at7.gemini.exceptions.GeminiException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@AutoConfigureMockMvc

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class RestAPIControllerSingleEntityTest extends UnitTestBase {

    //==== GEMINI TEST PREAMBOLE - WEBAPP APPLICANTION CONTEXT ====/
    static private MockMvc mockMvc;
    static ConfigurableApplicationContext webApp;

    @BeforeClass
    public static void setup() throws SQLException, GeminiException {
        setupWebMockMvc(setupFullWebAPP(RestAPIControllerSingleEntityTest.class));
    }

    @AfterClass
    public static void clean() {
        ConfigurableApplicationContext parent = (ConfigurableApplicationContext) webApp.getParent();
        parent.close();
        webApp.close();
    }

    public static void setupWebMockMvc(ConfigurableApplicationContext wApp) {
        webApp = wApp;
        mockMvc = webAppContextSetup((WebApplicationContext) wApp).build();
    }
    //=============================================================/

    @Test
    public void n1_saveEntity() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper(); // for json conversion

        //==== basic object -- with default value
        Map<String, Object> json = new HashMap<>();
        json.put("text", "lk");
        json.put("numberlong", 10);
        String jsonString = objectMapper.writeValueAsString(json);
        mockMvc.perform(post(API_PATH + "/TestDataType")
                .contentType(APPLICATION_JSON)
                .content(jsonString)
                .accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content()
                        // stric because new data type must fail
                        .json("{'bool':false,'text':'lk','domain1':{},'numberdouble':0,'numberlong':10, 'date':'', 'time': '', 'datetime':''}", true));
        // no duplicated keys
        mockMvc.perform(post(API_PATH + "/TestDataType")
                .contentType(APPLICATION_JSON)
                .content(jsonString)
                .accept(APPLICATION_JSON))
                .andExpect(status().is4xxClientError()) // posting an already existent
                .andExpect(content().json("{'errorcode':'MULTIPLE_LK_FOUND'}"));


        //==== basic object -- with all basic types value
        Map<String, Object> jsonAllBasicTypes = new HashMap<>();
        jsonAllBasicTypes.put("text", "lk-allBasicTypes");
        jsonAllBasicTypes.put("numberlong", 10);
        jsonAllBasicTypes.put("numberdouble", 11.1);
        jsonAllBasicTypes.put("date", "1989/9/6");
        jsonAllBasicTypes.put("time", "02:10");
        jsonAllBasicTypes.put("datetime", "1989-09-06T01:01");
        String jsonAllBtypeString = objectMapper.writeValueAsString(jsonAllBasicTypes);
        mockMvc.perform(post(API_PATH + "/TestDataType")
                .contentType(APPLICATION_JSON)
                .content(jsonAllBtypeString)
                .accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content()
                        // stric because new data type must fail
                        .json("{'bool':false,'text':'lk-allBasicTypes','domain1':{},'numberdouble':11.1,'numberlong':10, 'date':'1989-9-6', 'time': '02:10:00', 'datetime':'1989-09-06T01:01:00'}", true));


        //==== object with entity reference (FK) - single logical key
        Map<String, Object> domainJson = new HashMap<>();
        domainJson.put("code", "dm1");
        String domainJsonString = objectMapper.writeValueAsString(domainJson);
        mockMvc.perform(post(API_PATH + "/TestDomain1")
                .contentType(APPLICATION_JSON)
                .content(domainJsonString)
                .accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content()
                        .json("{'code':'dm1'}"));
        Map<String, Object> jsonWithDomain = new HashMap<>();
        jsonWithDomain.put("text", "lkWithDomain");
        jsonWithDomain.put("numberlong", 11);
        jsonWithDomain.put("domain1", "dm1");
        String dtWithDomain = objectMapper.writeValueAsString(jsonWithDomain);
        mockMvc.perform(post(API_PATH + "/TestDataType")
                .contentType(APPLICATION_JSON)
                .content(dtWithDomain)
                .accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content()
                        // stric because new data type must fail
                        .json("{'bool':false,'text':'lkWithDomain','domain1':'dm1','numberdouble':0,'numberlong':11}"));

        //=== lets POST a list of Objects
        List<Map<String, Object>> objList = new ArrayList<>();
        Map<String, Object> o1 = new HashMap<>();
        o1.put("text", "lk_list");
        o1.put("numberlong", 20);
        objList.add(o1);
        Map<String, Object> o2 = new HashMap<>();
        o2.put("text", "lkWithDomain_list");
        o2.put("numberlong", 22);
        o2.put("domain1", "dm1");
        objList.add(o2);
        String obkListJsonString = objectMapper.writeValueAsString(objList);
        mockMvc.perform(post(API_PATH + "/TestDataType")
                .contentType(APPLICATION_JSON)
                .content(obkListJsonString)
                .accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content()
                        .json("[ {'bool':false,'text':'lk_list','domain1':{},'numberdouble':0,'numberlong':20}, " +
                                "{'bool':false,'text':'lkWithDomain_list','domain1':'dm1','numberdouble':0,'numberlong':22}" +
                                "]"));


        //==== test entity with FK of to a hierarchy domain (LK with FK)
        Map<String, Object> domainHKJson = new HashMap<>();
        domainHKJson.put("code", "dmH1");
        domainHKJson.put("domain1", "dm1");
        String domainHJsonString = objectMapper.writeValueAsString(domainHKJson);
        mockMvc.perform(post(API_PATH + "/TestDomainHierarchy")
                .contentType(APPLICATION_JSON)
                .content(domainHJsonString)
                .accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content()
                        .json("{'code':'dmH1', 'domain1': 'dm1'}", true));
        Map<String, Object> dtWuthHierarchy = new HashMap<>();
        dtWuthHierarchy.put("text", "lkWithHKDomain");
        dtWuthHierarchy.put("dmHierarchy", domainHKJson);
        objList.add(o2);
        String dtWithHierarchyJSString = objectMapper.writeValueAsString(dtWuthHierarchy);
        mockMvc.perform(post(API_PATH + "/TestDataTypeWithHierachy")
                .contentType(APPLICATION_JSON)
                .content(dtWithHierarchyJSString)
                .accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content()
                        .json("{'text':'lkWithHKDomain','dmhierarchy':{'code':'dmH1', 'domain1': 'dm1'}}'", true));
    }

    @Test
    public void n2_testGetLk() throws Exception {

        //==== basic object -- with default value
        mockMvc.perform(get(API_PATH + "/TestDataType/lk")
                .accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content()
                        .json("{'bool':false,'text':'lk','domain1':{},'numberdouble':0,'numberlong':10}"));

        //==== basic object -- with all basic types value
        mockMvc.perform(get(API_PATH + "/TestDataType/lk-allBasicTypes")
                .accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content()
                        // stric because new data type must fail
                        .json("{'bool':false,'text':'lk-allBasicTypes','domain1':{},'numberdouble':11.1,'numberlong':10, 'date':'1989-9-6', 'time': '02:10:00', 'datetime':'1989-09-06T01:01:00'}", true));


        //==== object with entity reference (FK) - single logical key
        mockMvc.perform(get(API_PATH + "/TestDataType/lkWithDomain")
                .accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content()
                        .json("{'bool':false,'text':'lkWithDomain','domain1':'dm1','numberdouble':0,'numberlong':11}"));


        //==== test entity with FK of to a hierarchy domain (LK with FK)
        mockMvc.perform(get(API_PATH + "/TestDataTypeWithHierachy/lkWithHKDomain")
                .accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content()
                        .json("{'text':'lkWithHKDomain','dmhierarchy':{'code':'dmH1', 'domain1': 'dm1'}}'", true));
    }

    @Test
    public void n3_testUpdate() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper(); // for json conversion

        //==== basic object -- with default value
        Map<String, Object> json = new HashMap<>();
        json.put("numberlong", 100);
        String jsonString = objectMapper.writeValueAsString(json);
        mockMvc.perform(put(API_PATH + "/TestDataType/lk")
                .contentType(APPLICATION_JSON)
                .content(jsonString)
                .accept(APPLICATION_JSON))
                .andExpect(content()
                        // stric because new data type must fail
                        .json("{'bool':false,'text':'lk','domain1':{},'numberdouble':0,'numberlong':100, 'date':''}", false)); // TODO need strict true


        //==== update a domain and query the object that refers it
        //==== object with entity reference (FK) - single logical key
        Map<String, Object> domainJson = new HashMap<>();
        domainJson.put("code", "dm2");
        String domainJsonString = objectMapper.writeValueAsString(domainJson);
        mockMvc.perform(put(API_PATH + "/TestDomain1/dm1")
                .contentType(APPLICATION_JSON)
                .content(domainJsonString)
                .accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content()
                        .json("{'code':'dm2'}"));
        //==== object with entity reference (FK) - single logical key
        mockMvc.perform(get(API_PATH + "/TestDataType/lkWithDomain")
                .accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content()
                        .json("{'bool':false,'text':'lkWithDomain','domain1':'dm2','numberdouble':0,'numberlong':11}"));

    }

    @Test
    public void n4_testDelete() throws Exception {

        //==== basic object -- with default value
        mockMvc.perform(delete(API_PATH + "/TestDataType/lk")
                .accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content()
                        .json("{'bool':false,'text':'lk','domain1':{},'numberdouble':0,'numberlong':100, 'date':''}", false)); // TODO need strict true
        mockMvc.perform(get(API_PATH + "/TestDataType/lk")
                .accept(APPLICATION_JSON))
                .andExpect(status().is4xxClientError());


        //==== delete a domain and query the object that refers it
        mockMvc.perform(delete(API_PATH + "/TestDomain1/dm2")
                .accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content()
                        .json("{'code':'dm2'}"));
        mockMvc.perform(delete(API_PATH + "/TestDomain1/dm2")
                .accept(APPLICATION_JSON))
                .andExpect(status().is4xxClientError());
        //==== object with entity reference (FK) - single logical key
        mockMvc.perform(get(API_PATH + "/TestDataType/lkWithDomain")
                .accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content()
                        .json("{'bool':false,'text':'lkWithDomain','domain1':{},'numberdouble':0,'numberlong':11}"));
    }
}