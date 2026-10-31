package com.prismora.compat;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class PackAuditTest {
 @Test void unknownProgramRolesRemainExplicitlyUnresolved()throws Exception{var files=LegacyPackTest.fixture();files.put("shaders/Unknown.fsh",new byte[0]);var audit=PackAudit.audit(LegacyPack.inspect("test",files));assertTrue(audit.get("unresolvedConfigurationsOrPrograms").getAsInt()>0);}
 @Test void publishableSummaryExcludesSourceAndPropertyExpressions(){var audit=JsonParser.parseString("{\"pack\":\"test\",\"scope\":\"requirements\",\"unresolvedConfigurationsOrPrograms\":0,\"configurations\":[{\"profile\":\"LOW\",\"scenario\":\"base\",\"analysisOnly\":true,\"properties\":{\"uniform.float.private\":\"secretExpression()\"},\"programs\":[{\"status\":\"requirements_extracted\",\"source\":\"secretShaderCode\",\"uniforms\":{\"colortex0\":\"sampler2D\"},\"attributes\":{},\"builtins\":[]}]}]}").getAsJsonObject();String output=PackAuditSummary.summarize(audit).toString();assertTrue(output.contains("colortex0"));assertTrue(output.contains("uniform.float.private"));assertFalse(output.contains("secretExpression"));assertFalse(output.contains("secretShaderCode"));}
}
