/*
 * Copyright 2021 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package qr

import utils.AsyncHmrcSpec

class QRCodeSpec extends AsyncHmrcSpec {

  "generateDataImageBase64" should {
    "generate a base64 encoded image of a QR code" in {
      QRCode().generateDataImageBase64("Test") shouldBe "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABcAAAAXCAIAAABvSEP3AAAAu0lEQVR4Xq2OgQ7EIAxC/f+fvourUlrQXC4jman0wRyfNzTm55TEM+NUZQsyobU7nKzeUrrl/yWj5GXHKhkhr7sq9U0LS2n2WaVFlYR7BWu1nBTEL8o/xLDc7WAG0OZsCUtPDrSTU3PgQLpSB5+VDO/gsuA3wZwDuDYYlEzA6wzLirkYWhHkXzvcK2LQa7aszq1c78D92v+AuXO0NddLC3MWSLJlPFQBYKaFhRiujQFQWlRAT0kG+iv+0xe3smu/cFffegAAAABJRU5ErkJggg=="
    }
    "generate a base64 encoded image of a barcode from the supplied text with the specified scale" in {
      QRCode(5).generateDataImageBase64("QRCode test text") shouldBe "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAHMAAABzCAIAAAAkIaqxAAAFdUlEQVR4Xu3QQW7rSBAE0X//S8+s+QiEUKgm5YViV87IbJn//vvxDP/8w49D/L7sU/y+7FP8vuxT+GX/vQXvgvaEg1MjfNf7LXgXtCccnBrhu95vwbugPeHg1Ajf9X4L3gXtCQenRviu91vwLmhPODg1wne9097Qy51u+Nby7U57Qy93uuFby7c77Q293OmGby3f7rQ39HKnG761fLvT3tDLnW741vLtbntCd0cpHJRJm+6aerc9obujFA7KpE13Tb3bntDdUQoHZdKmu6bebU/o7iiFgzJp011T77YndHeUwkGZtOmuqXfbE7pLCiO56SnSprum3m1P6C4pjOSmp0ib7pp6tz2hu6QwkpueIm26a+rd9oTuksJIbnqKtOmuqXfbE7pLCiO56SnSprum3m1P6G6ngAwtj9Kmu6bebU/obqeADC2P0qa7pt5tT+hup4AMLY/Sprum3m1P6G6ngAwtj9Kmu6bebU/obqeADC2P0qa7pt5pb+jlUQrIMJJH9PLtTntDL49SQIaRPKKXb3faG3p5lAIyjOQRvXy7097Qy6MUkGEkj+jl2532hl4epYAMI3lEL9/ut+h3n0ufw3e936LffS59Dt/1fot+97n0OXzX+y363efS5/Bd77fod59Ln8N3ub/Fh1+5SL/Fn/kd+XU26bf4M78jv84m/RZ/5nfk19mk3+LP/I78Opv0W/g7+JXQMikgg/aVlkdpM+oig7HtKy2TAjJoX2l5lDajLjIY277SMikgg/aVlkdpM+oig7HtKy2TAjJoX2l5lDajLjIY277SMikgg/aVlkdpM+oig7HtCUw1lq9oX2m50w2jZWPKI5hqLF/RvtJypxtGy8aURzDVWL6ifaXlTjeMlo0pj2CqsXxF+0rLnW4YLRtTHsFUY/mK9pWWO90wWv4U5xbpc/AuaF9pudMRTnHDh/Jb8C5oX2m50xFOccOH8lvwLmhfabnTEU5xw4fyW/AuaF9pudMRTnHDh/Jb8C5oX2m50xFOeafd0IWDcqcbWG66e7vTbujCQbnTDSw33b3daTd04aDc6QaWm+7e7rQbunBQ7nQDy013b3faDV04KHe6geWmu7c77aa7oxSQoWXSDb1s6p12091RCsjQMumGXjb1Trvp7igFZGiZdEMvm3qn3XR3lAIytEy6oZdNvdNuujtKARlaJt3Qy6bc36J/JbQ8SgF5w8mtDaP/sOVRCsgbTm5tGP2HLY9SQN5wcmvD6D9seZQC8oaTWxtG/2HLoxSQN7jlU4/R727SDaNlZDC2/Rj97ibdMFpGBmPbj9HvbtINo2VkMLb9GP3uJt0wWkYGY9uP0e9u0g2jZWQwHpVH/JFl5GbUVfZOe8MfWUZuRl1l77Q3/JFl5GbUVfZOe8MfWUZuRl1l77Q3/JFl5GbUVfZue0J3SQH5OXw4GXWNu0zadJcUkJ/Dh5NR17jLpE13SQH5OXw4GXWNu0zadJcUkJ/Dh5NR17jLpE13SQH5OXw4GXWNu0zadHeTNt3tFJCh5dvd9oTubtKmu50CMrR8u9ue0N1N2nS3U0CGlm932xO6u0mb7nYKyNDy7W57Qnc3adPdTgEZWr7dbU/oLumG0fJB+UPq3faE7pJuGC0flD+k3m1P6C7phtHyQflD6t32hO6SbhgtH5Q/pN5tT+gu6YbR8kH5Q+qd9oZePphCy5sUlL3T3tDLB1NoeZOCsnfaG3r5YAotb1JQ9k57Qy8fTKHlTQrK3mlv6OWDKbS8SUHZ+y1G7yKD9pWNDDPZ+y1G7yKD9pWNDDPZ+y1G7yKD9pWNDDPZ+y1G7yKD9pWNDDPZ+y1G7yKD9pWNDDPZP/w4xO/LPsXvyz7F78s+xe/LPsX/wbMbngKyyn8AAAAASUVORK5CYII="
    }
  }
}
