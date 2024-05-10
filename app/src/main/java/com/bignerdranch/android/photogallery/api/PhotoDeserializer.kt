package com.bignerdranch.android.photogallery.api

import com.bignerdranch.android.photogallery.GalleryItem

import android.util.Log
import com.bignerdranch.android.photogallery.api.PhotoResponse
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type

//表示“PhotoDeserializer”专门针对“PhotoResponse”类型对象实现了“JsonDeserializer”接口。 这意味着它将提供自定义反序列化逻辑，用于将 JSON 转换为“PhotoResponse”对象。
class PhotoDeserializer : JsonDeserializer<PhotoResponse> {
    override fun deserialize( //每当 Gson 需要将 JSON 元素反序列化为“PhotoResponse”对象时，就会调用此函数。
        json: JsonElement, //类型为“JsonElement”。 这表示需要反序列化为“PhotoResponse”的 JSON 数据。
        typeOfT: Type?, //第二个参数“typeOfT”是一个“Type”对象，表示 Gson 期望的“PhotoResponse”的特定通用类型。 如果解串器处理更通用的情况，则可以使用它，但在这种情况下，不使用它。
        context: JsonDeserializationContext? //第三个参数`context`用于促进嵌套泛型类型的反序列化。 它允许您在嵌套元素上调用“反序列化”，遵守 Gson 配置
    ): PhotoResponse {
        Log.d("PhotoDeserializer", "Deserializing JSON: $json")
        val jsonObject = json.asJsonObject //将顶级 `JsonElement` (`json`) 转换为 `JsonObject`。 这是访问 JSON 对象的各个属性所必需的。
        val photosJson = jsonObject.getAsJsonObject("photos")  //从 `jsonObject` 中检索与键 `"photos"` 关联的 JSON 对象。 这是原始 JSON 的子集，特别是应映射到“PhotoResponse”的部分
        val photosArray = photosJson.getAsJsonArray("photo")  // Get the 'photo' array from 'photos' object
        //这使用“JsonDeserializationContext”（在方法参数中作为“context”提供）。
        //`context` 是 Gson 提供的一个工具，用于将 JSON 元素反序列化为 Java/Kotlin 对象。
        //使用“上下文”而不是直接反序列化允许该方法维护通用类型信息并重用其他地方定义的反序列化规则。
        //仅当“context”不为“null”时，“?”用于安全地调用“deserialize”。
        val items = context?.deserialize<List<GalleryItem>>(photosArray, object : TypeToken<List<GalleryItem>>() {}.type)
        //**`List<GalleryItem>`**：指定 JSON 数据应反序列化的类型。 在本例中，它指示 JSON 表示“GalleryItem”对象的列表。
        //**`photosArray`**：这是需要反序列化的 JSON 元素。 它应该是前面步骤中的 JSON 数组，其中“photo”数组是从 JSON 中的“photos”对象中提取的。
        //Gson 使用 `TypeToken` 来处理 Java/Kotlin 中的类型擦除。 此行为“List<GalleryItem>”创建“TypeToken”的匿名子类。 通过这样做，它捕获通用类型“List<GalleryItem>”，
        //以便 Gson 可以准确地将 JSON 反序列化为正确的类型。 “{}”表示该匿名子类的实例，“.type”检索封装的泛型类型。
        return PhotoResponse().apply {
            galleryItems = items ?: emptyList()
        }
        //        if (photosJson != null) {
//            // Deserialize the inner "photo" array safely
//            return context!!.deserialize(photosJson, PhotoResponse::class.java) ?: PhotoResponse()
//        } else {
//            // Return an empty PhotoResponse if "photos" is not found
//            return PhotoResponse()
//        }
    } //在 `context` 上调用 `deserialize` 方法，将 `photosJson` `JsonObject` 转换为 `PhotoResponse` 对象。 这是 Gson 使用自定义反序列化器将 JSON 转换为 Kotlin 数据模型的地方。 `!!` 运算符用于断言 `context` 不为空（在这种情况下永远不应该为空）。
//此函数有效地绕过了获取“PhotoResponse”所不需要的任何外部 JSON 层，使反序列化过程更加直接并根据您的特定需求进行定制。
}