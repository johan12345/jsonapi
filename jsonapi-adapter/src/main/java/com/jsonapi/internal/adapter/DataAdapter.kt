package com.jsonapi.internal.adapter

import com.jsonapi.Document
import com.jsonapi.JsonApiException
import com.jsonapi.internal.NAME_DATA
import com.jsonapi.internal.bind
import com.jsonapi.internal.binding.Unbinder
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonReader.Token
import com.squareup.moshi.JsonWriter

internal class DataAdapter(
  private val delegateAdapter: JsonAdapter<Document.Data<*>>
) : JsonAdapter<Document.Data<*>>() {
  
  override fun fromJson(reader: JsonReader): Document.Data<*>? {
    // In case of a null value deserialize to null and consume token
    if (reader.peek() == Token.NULL) {
      return reader.nextNull()
    }
    
    // Assert that root is a JSON object
    if (reader.peek() != Token.BEGIN_OBJECT) {
      throw JsonApiException("A JSON object MUST be at the root of every JSON:API document but found ${reader.peek()}")
    }
    
    // Deserialize document and bind its resources
    val document = delegateAdapter.fromJson(reader)
    document?.bind()
    return document
  }
  
  override fun toJson(writer: JsonWriter, value: Document.Data<*>?) {
    if (value == null) {
      writer.nullValue()
      return
    }
    
    if (value.data == null && value.meta == null) {
      // Serialize null data document as {"data":null} since it is a valid document per specification
      val wasSerializeNulls = writer.serializeNulls
      writer.serializeNulls = true
      writer.beginObject().name(NAME_DATA).nullValue().endObject()
      writer.serializeNulls = wasSerializeNulls
    } else {
      // Transform document for serialization by unbinding relationship fields
      val unbinder = Unbinder(value)
      unbinder.unbind()
      // When included needs to be omitted from serialization remove them from the document
      if (value.serializationRules?.serializeIncluded == false) {
        unbinder.removeIncluded()
      }
      // Serialize transformed document with delegate adapter
      delegateAdapter.toJson(writer, value)
      // When included were omitted for serialization assign them back to the document
      if (value.serializationRules?.serializeIncluded == false) {
        unbinder.assignIncluded()
      }
      // Bind document back so that primary resource(s) are not changed
      value.bind()
    }
  }
}