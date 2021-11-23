package xln.common.converter

import com.google.protobuf.Message
import com.google.protobuf.util.JsonFormat
import com.mongodb.BasicDBObject
import com.mongodb.DBObject
import org.bson.Document
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.ReadingConverter
import org.springframework.data.convert.WritingConverter
import xln.common.proto.api.Api
import xln.common.utils.ProtoUtils

@WritingConverter
class ProtobufWriter(private val typeRegistry: JsonFormat.TypeRegistry) : Converter<Message, DBObject> {
    override fun convert(value: Message): DBObject? {
        val json = ProtoUtils.jsonUsingType(value, typeRegistry)
        if(json == null) {
            throw RuntimeException("Protobuf Message convert fail")
        }
        return BasicDBObject.parse(json)
    }

}

@ReadingConverter
class ProtobufAnyReader(private val typeRegistry: JsonFormat.TypeRegistry) : Converter<Document, com.google.protobuf.Any> {
    override fun convert(source: Document):  com.google.protobuf.Any? {

        val json = source.toJson();
        return ProtoUtils.fromJson(json, com.google.protobuf.Any::class.java, typeRegistry)
    }

}


@ReadingConverter
class ApiReader : Converter<Document, Api.HttpApi> {
    override fun convert(source: Document): Api.HttpApi? {
        val json = source.toJson();
        return ProtoUtils.fromJson(json, Api.HttpApi::class.java)

    }

}