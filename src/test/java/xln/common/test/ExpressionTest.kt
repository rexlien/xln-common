package xln.common.test

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit4.SpringRunner
import xln.common.expression.*
import xln.common.expression.v2.ValueCondition
import xln.common.utils.CollectionUtils
import xln.common.xln.common.extension.startEvalAsync

private val log = KotlinLogging.logger {}


@RunWith(SpringRunner::class)
@SpringBootTest(classes = [TestApplication::class])
@Import(UtilTestKt.TestHandler::class)
@ActiveProfiles("test")
class ExpressionTest {

    @Test
    fun testPathGet() {
        val testMap: MutableMap<String, Any> = HashMap()
        val layer2: MutableMap<String, Any> = HashMap()
        val layer3 = 1
        testMap["layer2"] = layer2
        layer2["layer3"] = layer3
        var res: Any? = CollectionUtils.pathGet("layer2/layer3", testMap)
        Assert.assertTrue(res as Int == 1)
        res = CollectionUtils.pathGet("layer2/layer3/layer4", testMap)
        Assert.assertTrue(res == null)
        res = CollectionUtils.pathGet("layer2/layer4", testMap)
        Assert.assertTrue(res == null)
        res = CollectionUtils.pathGet("layer2/layer4/layer5", testMap)
        Assert.assertTrue(res == null)
    }

    private object Mapper {
        val mapper: ObjectMapper = ObjectMapper().registerModule(KotlinModule())

    }

    val content = """
      {
          "actions": [
                {
                    "id": "1",
                    "key": "key1",
                    "progress": "1"
                
                },
                {
                    "id": "2",
                    "key": "key2",
                    "progress": "2"
                },
                {
                    "id": "2",
                    "key": "key2",
                    "progress": 3
                }
          ]
      }
    """.trimIndent()

    @Test
    fun testJsonReadArrayPathGet() {

        val body: Map<String, Any> = Mapper.mapper.readValue(content)

        runBlocking {

            var obj = CollectionUtils.pathGet("actions", body)
            Assert.assertTrue(obj as ArrayList<*> != null);

            obj = CollectionUtils.pathGet("actions[0]", body)
            Assert.assertTrue(obj as Map<*, *> != null);

            obj = CollectionUtils.pathGet("//actions[0]/key", body)
            Assert.assertTrue(obj as String == "key1");

            obj = CollectionUtils.pathGet("actions[]", body)
            Assert.assertTrue(obj == null)

            obj = CollectionUtils.pathGet("actions[aa]", body)
            Assert.assertTrue(obj == null)

            obj = CollectionUtils.pathGet("actions[2]", body)
            Assert.assertTrue(obj == null)


        }
    }

    @Test
    fun testValueEvaluator() {

        runBlocking {


            val evaluator = ConditionEvaluator(Context())
            var root = LogicalOperator()
            root.addElements(
                ValueCondition().setSrcValue(ConstantValue("7")).setOp(Const.OP_TYPE_GREATER).setTargetValue(6)
            )

            Assert.assertTrue(evaluator.startEvalAsync(root) as Boolean)

            root = LogicalOperator()
            root.addElements(
                ValueCondition().setSrcValue(ConstantValue("6")).setOp(Const.OP_TYPE_GREATER_OR_EQUAL).setTargetValue(6)
            )
            Assert.assertTrue(evaluator.startEvalAsync(root) as Boolean)

            root = LogicalOperator()
            root.addElements(
                ValueCondition().setSrcValue(ConstantValue("5")).setOp(Const.OP_TYPE_LESS).setTargetValue(6)
            )
            Assert.assertTrue(evaluator.startEvalAsync(root) as Boolean)

            root = LogicalOperator()
            root.addElements(
                ValueCondition().setSrcValue(ConstantValue(5)).setOp(Const.OP_TYPE_LESS).setTargetValue("6")
            )
            Assert.assertTrue(evaluator.startEvalAsync(root) as Boolean)

            root = LogicalOperator()
            root.addElements(
                ValueCondition().setSrcValue(ConstantValue("6")).setOp(Const.OP_TYPE_EQUAL).setTargetValue("6")
            )
            Assert.assertTrue(evaluator.startEvalAsync(root) as Boolean)


        }
    }

    @Test
    fun testConditionEvaluator() {
        runBlocking {
            val server = MockWebServer()
            val response = MockResponse()
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .setBody(content);


            server.enqueue(response)
            server.enqueue(response)
            server.start()

            val url = server.url("/expressionTest/action").toString()

            val conditionSourcePath = "${url}?_resPath=actions[0]/progress"
            val action2Progress = "${url}?_resPath=actions[2]/progress"
            //condition comparing target, (Any typed) could be integer or string
            val conditionTarget = 1

            val context = Context(GeneralDataProvider())
            val evaluator = ConditionEvaluator(context)
            var root = LogicalOperator()

            root.addElements(Condition(conditionSourcePath, Const.OP_TYPE_GREATER_OR_EQUAL, 1))
            //root.addElements(

            var boolResult = evaluator.startEvalAsync(root)
            Assert.assertTrue(boolResult as Boolean)

            root = LogicalOperator()
            root.addElements(Condition(action2Progress, Const.OP_TYPE_GREATER_OR_EQUAL, "3"))
            boolResult = evaluator.startEvalAsync(root)
            Assert.assertTrue(boolResult as Boolean)

            server.shutdown()
        }

    }

    @Test
    fun testProgressConditionEvaluator() {
        runBlocking {
            val server = MockWebServer()
            val response = MockResponse()
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .setBody(content);


            server.enqueue(response)
            server.enqueue(response)
            server.start()

            val url = server.url("/expressionTest/action").toString()

            val conditionSourcePath = "${url}?_resPath=actions[0]/progress"
            val action2Progress = "${url}?_resPath=actions[2]/progress"
            //condition comparing target, (Any typed) could be integer or string

            val context = Context(GeneralDataProvider())
            val evaluator = ProgressConditionEvaluator(context)
            var root = LogicalOperator()

            root.addElements(Condition(conditionSourcePath, Const.OP_TYPE_GREATER_OR_EQUAL, 1))
            //root.addElements(

            var result = evaluator.startEvalAsync(root)
            Assert.assertTrue(result.result)

            root = LogicalOperator()
            root.addElements(Condition(action2Progress, Const.OP_TYPE_GREATER_OR_EQUAL, "3"))
            result = evaluator.startEvalAsync(root) as Result
            Assert.assertTrue(result.result)

            server.shutdown()
        }

    }

}


